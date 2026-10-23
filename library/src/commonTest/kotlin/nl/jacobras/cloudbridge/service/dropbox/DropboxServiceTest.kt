package nl.jacobras.cloudbridge.service.dropbox

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.single
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import nl.jacobras.cloudbridge.CloudBridge.applyDefaults
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.bufferOf
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import kotlin.test.Test
import kotlin.time.Instant

class DropboxServiceTest {

    @Test
    fun `expired access token throws NotAuthenticatedException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{".tag":"expired_access_token"},"error_summary":"expired_access_token/"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("expired"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getUserInfo() }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo("https://api.dropboxapi.com/2/users/get_current_account")
        }
    }

    @Test
    fun `downloading missing file throws NotFoundException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{".tag":"path","path":{".tag":"not_found"}},"error_summary":"path/not_found/"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.downloadFile(CloudItemId("id:missing")) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
            .hasMessage("The requested item 'id:missing' could not be found")
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo("https://content.dropboxapi.com/2/files/download")
            assertThat(it.headers["Dropbox-API-Arg"]).isEqualTo("""{"path":"id:missing"}""")
        }
    }

    @Test
    fun `uploading without space left throws InsufficientSpaceException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error_summary":"path/insufficient_space/","error":{".tag":"path","reason":{".tag":"insufficient_space"},"upload_session_id":"AAAAAAAAAAAAAAAAAAAAAA"}}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.createFile("/big.txt".asFilePath(), "content") }
            .isInstanceOf<CloudServiceException.InsufficientSpaceException>()
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo("https://content.dropboxapi.com/2/files/upload")
        }
    }

    @Test
    fun `create file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"name":"asdf.txt","path_lower":"/asdf.txt","path_display":"/asdf.txt","id":"id:uwStUuhj4qYAAAAAAAAAIQ","client_modified":"2025-10-11T16:30:47Z","server_modified":"2025-10-11T16:30:47Z","rev":"01640e48e7e6a010000000301ae1691","size":1,"is_downloadable":true,"content_hash":"80903da4e6bbdf96e8ff6fc3966b0cfd355c7e860bdd1caa8e4722d9230e40ac"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.createFile("/asdf.txt".asFilePath(), "a")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"),
                path = "/asdf.txt".asFilePath(),
                name = "asdf.txt",
                sizeInBytes = 1,
                modified = Instant.parse("2025-10-11T16:30:47Z"),
                revision = "01640e48e7e6a010000000301ae1691"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo("https://content.dropboxapi.com/2/files/upload")
        }
    }

    @Test
    fun `create folder`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"metadata":{"name":"Work","path_lower":"/work","path_display":"/Work","id":"id:uwStUuhj4qYAAAAAAAAACg"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.createFolder("/Work".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("id:uwStUuhj4qYAAAAAAAAACg"),
                path = "/work".asFolderPath(),
                name = "Work"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo("https://api.dropboxapi.com/2/files/create_folder_v2")
        }
    }

    @Test
    fun `small binary file uploads in a single request`() = runTest {
        val engine = MockEngine {
            respond(
                content = UPLOAD_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.createFile("/image.png".asFilePath(), bufferOf(3), mimeType = "image/png")

        assertThat(file.id).isEqualTo(CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"))
        assertThat(engine.requestHistory).single().given {
            assertThat(it.url.toString()).isEqualTo("https://content.dropboxapi.com/2/files/upload")
            assertThat(it.headers["Dropbox-API-Arg"]).isEqualTo("""{"path":"/image.png","mode":"add"}""")
            assertThat(it.body.toByteArray().toList()).isEqualTo(listOf<Byte>(0, 1, 2))
        }
    }

    @Test
    fun `large binary file uploads in a session`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = when {
                    request.url.encodedPath.endsWith("/start") -> """{"session_id":"session1"}"""
                    request.url.encodedPath.endsWith("/finish") -> UPLOAD_RESPONSE
                    else -> "null"
                },
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )
        service.uploadChunkSize = 4

        val file = service.createFile("/archive.zip".asFilePath(), bufferOf(10))

        assertThat(file.revision).isEqualTo("01640e48e7e6a010000000301ae1691")
        assertThat(engine.requestHistory.map { it.url.toString() }).containsExactly(
            "https://content.dropboxapi.com/2/files/upload_session/start",
            "https://content.dropboxapi.com/2/files/upload_session/append_v2",
            "https://content.dropboxapi.com/2/files/upload_session/finish"
        )
        assertThat(engine.requestHistory.map { it.headers["Dropbox-API-Arg"] }).containsExactly(
            """{}""",
            """{"cursor":{"session_id":"session1","offset":4}}""",
            """{"cursor":{"session_id":"session1","offset":8},"commit":{"path":"/archive.zip","mode":"add"}}"""
        )
        assertThat(engine.requestHistory.map { it.body.toByteArray().size }).containsExactly(4, 4, 2)
    }

    @Test
    fun `list files in root`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "cursor": "AAbE9lk4CB2ifC9iAVvqjBVcU5bfIefn9XZ8I1x1O9Bu6bNLKWaTxHxA7knqxKTdbHEMiT5D93SUOWbvp1zM4o2kEd2uCD-O8CJXr5hcEdokwZ3850UqZRJY_A_EQOvG82kd8Kbx4S7HbNyiEvIP5XQLj5Cr5CRXZ7WuFvj-ofIaA453LXsRhK8KrdbXLZTokMU",
                      "entries": [
                        {
                          ".tag": "folder",
                          "id": "id:uwStUuhj4qYAAAAAAAAACg",
                          "name": "Work",
                          "path_display": "/Work",
                          "path_lower": "/work",
                          "property_groups": []
                        },
                        {
                          ".tag": "file",
                          "client_modified": "2025-10-11T16:30:47Z",
                          "content_hash": "80903da4e6bbdf96e8ff6fc3966b0cfd355c7e860bdd1caa8e4722d9230e40ac",
                          "id": "id:uwStUuhj4qYAAAAAAAAAIQ",
                          "is_downloadable": true,
                          "name": "asdf.txt",
                          "path_display": "/asdf.txt",
                          "path_lower": "/asdf.txt",
                          "property_groups": [],
                          "rev": "01640e48e7e6a010000000301ae1691",
                          "server_modified": "2025-10-11T16:30:47Z",
                          "size": 1
                        }
                      ],
                      "has_more": false
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val items = service.listFiles(FolderPath("/"))

        assertThat(items).containsExactly(
            CloudFolder(
                id = CloudItemId("id:uwStUuhj4qYAAAAAAAAACg"),
                path = "/work".asFolderPath(),
                name = "Work"
            ),
            CloudFile(
                id = CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"),
                path = "/asdf.txt".asFilePath(),
                name = "asdf.txt",
                sizeInBytes = 1,
                modified = Instant.parse("2025-10-11T16:30:47Z"),
                revision = "01640e48e7e6a010000000301ae1691"
            )
        )
        val request = engine.requestHistory.single()
        assertThat(request.method).isEqualTo(HttpMethod.Post)
        assertThat(request.url.toString()).isEqualTo("https://api.dropboxapi.com/2/files/list_folder")
        assertThat(request.body.toByteArray().decodeToString()).isEqualTo("""{"path":""}""")
    }

    @Test
    fun `delete file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"metadata":{".tag":"file","client_modified":"2025-10-11T16:30:47Z","content_hash":"80903da4e6bbdf96e8ff6fc3966b0cfd355c7e860bdd1caa8e4722d9230e40ac","id":"id:uwStUuhj4qYAAAAAAAAAIQ","is_downloadable":true,"name":"asdf.txt","path_display":"/asdf.txt","path_lower":"/asdf.txt","property_groups":[],"rev":"01640e48e7e6a010000000301ae1691","server_modified":"2025-10-11T16:30:47Z","size":1}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        service.delete(CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"))

        val request = engine.requestHistory.single()
        assertThat(request.method).isEqualTo(HttpMethod.Post)
        assertThat(request.url.toString()).isEqualTo("https://api.dropboxapi.com/2/files/delete_v2")
        assertThat(
            request.body.toByteArray().decodeToString()
        ).isEqualTo("""{"path":"id:uwStUuhj4qYAAAAAAAAAIQ"}""")
    }
}

private const val UPLOAD_RESPONSE = """{"name":"asdf.txt","path_lower":"/asdf.txt","path_display":"/asdf.txt","id":"id:uwStUuhj4qYAAAAAAAAAIQ","client_modified":"2025-10-11T16:30:47Z","server_modified":"2025-10-11T16:30:47Z","rev":"01640e48e7e6a010000000301ae1691","size":1}"""