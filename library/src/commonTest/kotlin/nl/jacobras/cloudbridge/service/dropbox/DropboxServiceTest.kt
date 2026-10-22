package nl.jacobras.cloudbridge.service.dropbox

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
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
                modified = Instant.parse("2025-10-11T16:30:47Z")
            )
        )
        val request = engine.requestHistory.single()
        assertThat(request.method).isEqualTo(HttpMethod.Post)
        assertThat(request.url.toString()).isEqualTo("https://api.dropboxapi.com/2/files/list_folder")
        assertThat(request.body.toByteArray().decodeToString()).isEqualTo("""{"path":""}""")
    }
}