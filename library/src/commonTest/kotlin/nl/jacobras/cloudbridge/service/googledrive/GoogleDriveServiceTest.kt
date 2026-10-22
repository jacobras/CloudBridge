package nl.jacobras.cloudbridge.service.googledrive

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

class GoogleDriveServiceTest {

    @Test
    fun `invalid access token throws NotAuthenticatedException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "error": {
                        "code": 401,
                        "message": "Request had invalid authentication credentials. Expected OAuth 2 access token, login cookie or other valid authentication credential. See https://developers.google.com/identity/sign-in/web/devconsole-project.",
                        "errors": [
                          {
                            "message": "Invalid Credentials",
                            "domain": "global",
                            "reason": "authError",
                            "location": "Authorization",
                            "locationType": "header"
                          }
                        ],
                        "status": "UNAUTHENTICATED"
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("invalid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getUserInfo() }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://www.googleapis.com/drive/v3/about?fields=user")
        }
    }

    @Test
    fun `downloading missing file throws NotFoundException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "error": {
                        "code": 404,
                        "message": "File not found: 1mVq7Rk2XwTz9LpHc4Ns8DfJy3Gb6Ue5Ao0Ki1WrQxSdZtYhPg.",
                        "errors": [
                          {
                            "message": "File not found: 1mVq7Rk2XwTz9LpHc4Ns8DfJy3Gb6Ue5Ao0Ki1WrQxSdZtYhPg.",
                            "domain": "global",
                            "reason": "notFound",
                            "location": "fileId",
                            "locationType": "parameter"
                          }
                        ]
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.downloadFile(CloudItemId("1mVq7Rk2XwTz9LpHc4Ns8DfJy3Gb6Ue5Ao0Ki1WrQxSdZtYhPg")) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
            .hasMessage("The requested item '1mVq7Rk2XwTz9LpHc4Ns8DfJy3Gb6Ue5Ao0Ki1WrQxSdZtYhPg' could not be found")
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://www.googleapis.com/drive/v3/files/1mVq7Rk2XwTz9LpHc4Ns8DfJy3Gb6Ue5Ao0Ki1WrQxSdZtYhPg?alt=media")
        }
    }

    @Test
    fun `list files in root`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "files": [
                        {
                          "mimeType": "application/vnd.google-apps.folder",
                          "parents": [
                            "1THG-A2vXis2oKtcHO7fK6u_1VLRN"
                          ],
                          "id": "131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA",
                          "name": "Work",
                          "trashed": false,
                          "modifiedTime": "2025-10-11T15:22:38.925Z",
                          "version": "1"
                        },
                        {
                          "mimeType": "text/plain",
                          "parents": [
                            "1THG-A2vXis2oKtcHO7fK6u_1VLRN"
                          ],
                          "size": "10",
                          "id": "1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg",
                          "name": "hello.txt",
                          "trashed": false,
                          "modifiedTime": "2025-10-11T16:31:27.438Z",
                          "version": "5"
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val items = service.listFiles(FolderPath("/"))

        assertThat(items).containsExactly(
            CloudFolder(
                id = CloudItemId("131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA"),
                path = "/131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA".asFolderPath(),
                name = "Work"
            ),
            CloudFile(
                id = CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
                path = "/hello.txt".asFilePath(),
                name = "hello.txt",
                sizeInBytes = 10,
                modified = Instant.parse("2025-10-11T16:31:27.438Z")
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo(
                "https://www.googleapis.com/drive/v3/files?q=%27appDataFolder%27+in+parents&spaces=appDataFolder" +
                    "&fields=nextPageToken%2Cfiles%28id%2Cname%2CmimeType%2Csize%2CmodifiedTime%2Cparents%2Cversion%2Ctrashed%29" +
                    "&pageSize=100"
            )
        }
    }

    @Test
    fun `delete file`() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.NoContent
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        service.delete(CloudItemId("1lH3t_0YffDLwRj5pr1LZGu386IJ9XHaCOu3MSxApjShLLUD-fg"))

        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Delete)
            assertThat(it.url.toString()).isEqualTo(
                "https://www.googleapis.com/drive/v3/files/1lH3t_0YffDLwRj5pr1LZGu386IJ9XHaCOu3MSxApjShLLUD-fg"
            )
        }
    }
}