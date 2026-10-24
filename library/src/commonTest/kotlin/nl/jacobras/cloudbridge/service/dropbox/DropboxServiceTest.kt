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
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import nl.jacobras.cloudbridge.CloudBridge.applyDefaults
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import nl.jacobras.cloudbridge.util.bufferOf
import nl.jacobras.cloudbridge.util.examplePngBytes
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
                content = NOT_FOUND_RESPONSE,
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
    fun `download binary file`() = runTest {
        val bytes = examplePngBytes()
        val engine = MockEngine {
            respond(
                content = bytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val sink = Buffer()
        service.downloadFile(CloudItemId("id:abc"), sink)

        assertThat(sink.readByteArray().toList()).isEqualTo(bytes.toList())
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo("https://content.dropboxapi.com/2/files/download")
            assertThat(it.headers["Dropbox-API-Arg"]).isEqualTo("""{"path":"id:abc"}""")
        }
    }

    @Test
    fun `downloading missing binary file throws NotFoundException`() = runTest {
        val engine = MockEngine {
            respond(
                content = NOT_FOUND_RESPONSE,
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.downloadFile(CloudItemId("id:missing"), Buffer()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
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
                content = """{"name":"Welcome.txt","path_lower":"/welcome.txt","path_display":"/Welcome.txt","id":"id:uwStUuhj4qYAAAAAAAAAIQ","client_modified":"2025-10-11T16:30:47Z","server_modified":"2025-10-11T16:30:47Z","rev":"01640e48e7e6a010000000301ae1691","size":1,"is_downloadable":true,"content_hash":"80903da4e6bbdf96e8ff6fc3966b0cfd355c7e860bdd1caa8e4722d9230e40ac"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.createFile("/Welcome.txt".asFilePath(), "a")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"),
                path = "/Welcome.txt".asFilePath(),
                name = "Welcome.txt",
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
    fun `update file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"name":"Welcome.txt","path_lower":"/notes/welcome.txt","path_display":"/Notes/Welcome.txt","id":"id:uwStUuhj4qYAAAAAAAAAIQ","client_modified":"2025-10-12T10:00:00Z","server_modified":"2025-10-12T10:00:00Z","rev":"01640e48e7e6a010000000301ae1692","size":2,"is_downloadable":true,"content_hash":"80903da4e6bbdf96e8ff6fc3966b0cfd355c7e860bdd1caa8e4722d9230e40ac"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.updateFile(CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"), "ab")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"),
                path = "/Notes/Welcome.txt".asFilePath(),
                name = "Welcome.txt",
                sizeInBytes = 2,
                modified = Instant.parse("2025-10-12T10:00:00Z"),
                revision = "01640e48e7e6a010000000301ae1692"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.url.toString()).isEqualTo("https://content.dropboxapi.com/2/files/upload")
            assertThat(it.headers["Dropbox-API-Arg"])
                .isEqualTo("""{"path":"id:uwStUuhj4qYAAAAAAAAAIQ","mode":"overwrite"}""")
        }
    }

    @Test
    fun `move file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"metadata":{".tag":"file","name":"new.txt","path_lower":"/notes/new.txt","path_display":"/Notes/new.txt","id":"id:uwStUuhj4qYAAAAAAAAAIQ","client_modified":"2025-10-11T16:30:47Z","rev":"01640e48e7e6a010000000301ae1692","size":1}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.moveFile(
            CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"),
            "/Notes/new.txt".asFilePath()
        )

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"),
                path = "/Notes/new.txt".asFilePath(),
                name = "new.txt",
                sizeInBytes = 1,
                modified = Instant.parse("2025-10-11T16:30:47Z"),
                revision = "01640e48e7e6a010000000301ae1692"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.url.toString()).isEqualTo("https://api.dropboxapi.com/2/files/move_v2")
            assertThat(it.body.toByteArray().decodeToString())
                .isEqualTo("""{"from_path":"id:uwStUuhj4qYAAAAAAAAAIQ","to_path":"/Notes/new.txt"}""")
        }
    }

    @Test
    fun `move folder`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"metadata":{".tag":"folder","name":"New","path_lower":"/new","path_display":"/New","id":"id:folder1"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.moveFolder(CloudItemId("id:folder1"), "/New".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("id:folder1"),
                path = "/New".asFolderPath(),
                name = "New"
            )
        )
        assertThat(engine.requestHistory.single().body.toByteArray().decodeToString())
            .isEqualTo("""{"from_path":"id:folder1","to_path":"/New"}""")
    }

    @Test
    fun `moving to an existing path throws ConflictException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error_summary":"to/conflict/file/..","error":{".tag":"to","to":{".tag":"conflict","conflict":{".tag":"file"}}}}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.moveFile(CloudItemId("id:abc"), "/taken.txt".asFilePath()) }
            .isInstanceOf<CloudServiceException.ConflictException>()
    }

    @Test
    fun `moving a folder to the root throws IllegalArgumentException`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.OK) }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.moveFolder(CloudItemId("id:abc"), "/".asFolderPath()) }
            .isInstanceOf<IllegalArgumentException>()
        assertThat(engine.requestHistory.size).isEqualTo(0)
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
                path = "/Work".asFolderPath(),
                name = "Work"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo("https://api.dropboxapi.com/2/files/create_folder_v2")
        }
    }

    @Test
    fun `create nested folder`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"metadata":{"name":"Sub","path_lower":"/work/sub","path_display":"/Work/Sub","id":"id:abc"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.createFolder("/Work/Sub".asFolderPath())

        assertThat(folder.path).isEqualTo("/Work/Sub".asFolderPath())
        assertThat(engine.requestHistory).single().given {
            assertThat(it.body.toByteArray().decodeToString()).isEqualTo("""{"path":"/Work/Sub"}""")
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

        val file =
            service.createFile("/image.png".asFilePath(), bufferOf(3), mimeType = "image/png")

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
        assertThat(engine.requestHistory.map { it.body.toByteArray().size }).containsExactly(
            4,
            4,
            2
        )
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
                          "name": "Welcome.txt",
                          "path_display": "/Welcome.txt",
                          "path_lower": "/welcome.txt",
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
                path = "/Work".asFolderPath(),
                name = "Work"
            ),
            CloudFile(
                id = CloudItemId("id:uwStUuhj4qYAAAAAAAAAIQ"),
                path = "/Welcome.txt".asFilePath(),
                name = "Welcome.txt",
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
    fun `get file info`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{".tag":"file","name":"Welcome.txt","path_lower":"/notes/welcome.txt","path_display":"/Notes/Welcome.txt","id":"id:file1","client_modified":"2025-10-11T16:30:47Z","rev":"0164","size":1}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.getFileInfo("/Notes/Welcome.txt".asFilePath())

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("id:file1"),
                path = "/Notes/Welcome.txt".asFilePath(),
                name = "Welcome.txt",
                sizeInBytes = 1,
                modified = Instant.parse("2025-10-11T16:30:47Z"),
                revision = "0164"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.url.toString()).isEqualTo("https://api.dropboxapi.com/2/files/get_metadata")
            assertThat(
                it.body.toByteArray().decodeToString()
            ).isEqualTo("""{"path":"/Notes/Welcome.txt"}""")
        }
    }

    @Test
    fun `get file info throws NotFoundException when missing`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error_summary":"path/not_found/..","error":{".tag":"path","path":{".tag":"not_found"}}}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFileInfo("/missing.txt".asFilePath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
    }

    @Test
    fun `get file info throws NotFoundException for a folder`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{".tag":"folder","name":"Notes","path_lower":"/notes","path_display":"/Notes","id":"id:folder1"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFileInfo("/Notes".asFilePath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
    }

    @Test
    fun `get folder info`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{".tag":"folder","name":"Notes","path_lower":"/notes","path_display":"/Notes","id":"id:folder1"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.getFolderInfo("/Notes".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("id:folder1"),
                path = "/Notes".asFolderPath(),
                name = "Notes"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.url.toString()).isEqualTo("https://api.dropboxapi.com/2/files/get_metadata")
            assertThat(it.body.toByteArray().decodeToString()).isEqualTo("""{"path":"/Notes"}""")
        }
    }

    @Test
    fun `get folder info throws NotFoundException for a file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{".tag":"file","name":"Welcome.txt","path_lower":"/welcome.txt","path_display":"/Welcome.txt","id":"id:file1","client_modified":"2025-10-11T16:30:47Z","rev":"0164","size":1}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFolderInfo("/Welcome.txt".asFolderPath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
    }

    @Test
    fun `get folder info for root throws IllegalArgumentException`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.OK) }
        val service = DropboxService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFolderInfo("/".asFolderPath()) }
            .isInstanceOf<IllegalArgumentException>()
        assertThat(engine.requestHistory.size).isEqualTo(0)
    }

    @Test
    fun `delete file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"metadata":{".tag":"file","client_modified":"2025-10-11T16:30:47Z","content_hash":"80903da4e6bbdf96e8ff6fc3966b0cfd355c7e860bdd1caa8e4722d9230e40ac","id":"id:uwStUuhj4qYAAAAAAAAAIQ","is_downloadable":true,"name":"Welcome.txt","path_display":"/Welcome.txt","path_lower":"/welcome.txt","property_groups":[],"rev":"01640e48e7e6a010000000301ae1691","server_modified":"2025-10-11T16:30:47Z","size":1}}""",
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

private const val UPLOAD_RESPONSE =
    """{"name":"Welcome.txt","path_lower":"/welcome.txt","path_display":"/Welcome.txt","id":"id:uwStUuhj4qYAAAAAAAAAIQ","client_modified":"2025-10-11T16:30:47Z","server_modified":"2025-10-11T16:30:47Z","rev":"01640e48e7e6a010000000301ae1691","size":1}"""

private const val NOT_FOUND_RESPONSE =
    """{"error":{".tag":"path","path":{".tag":"not_found"}},"error_summary":"path/not_found/"}"""