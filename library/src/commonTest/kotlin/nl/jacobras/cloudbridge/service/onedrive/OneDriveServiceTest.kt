package nl.jacobras.cloudbridge.service.onedrive

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
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

class OneDriveServiceTest {

    @Test
    fun `invalid access token throws NotAuthenticatedException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"InvalidAuthenticationToken","message":"AuthenticationException","innerError":{"date":"2026-10-22T13:24:33","request-id":"c436f56e-996f-49c5-adbb-9374b3a5db09","client-request-id":"c436f56e-996f-49c5-adbb-9374b3a5db09"}}}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("invalid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getUserInfo() }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://graph.microsoft.com/oidc/userinfo")
        }
    }

    @Test
    fun `list files in root`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "value": [
                        {
                          "createdDateTime": "2025-10-11T15:22:39Z",
                          "id": "33488461260C4A76!sc4581cc0f018449d9f07fb9cf832adab",
                          "lastModifiedDateTime": "2025-10-11T15:22:39Z",
                          "name": "Work",
                          "size": 0,
                          "parentReference": {
                            "driveType": "personal",
                            "id": "33488461260C4A76!s917d5fe70450423e896e4f9ab0023434",
                            "name": "CloudBridge Demo",
                            "path": "/drive/root:/Apps/CloudBridge Demo"
                          },
                          "folder": {
                            "childCount": 0
                          }
                        },
                        {
                          "createdDateTime": "2025-10-11T16:13:38Z",
                          "id": "33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc",
                          "lastModifiedDateTime": "2025-10-11T16:32:05Z",
                          "name": "asdf.txt",
                          "size": 1,
                          "parentReference": {
                            "driveType": "personal",
                            "id": "33488461260C4A76!s917d5fe70450423e896e4f9ab0023434",
                            "name": "CloudBridge Demo",
                            "path": "/drive/root:/Apps/CloudBridge Demo"
                          },
                          "file": {
                            "mimeType": "text/plain"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val items = service.listFiles(FolderPath("/"))

        assertThat(items).containsExactly(
            CloudFolder(
                id = CloudItemId("33488461260C4A76!sc4581cc0f018449d9f07fb9cf832adab"),
                path = "Work".asFolderPath(),
                name = "Work"
            ),
            CloudFile(
                id = CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"),
                path = "asdf.txt".asFilePath(),
                name = "asdf.txt",
                sizeInBytes = 1,
                modified = Instant.parse("2025-10-11T16:32:05Z")
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://graph.microsoft.com/v1.0/me/drive/special/approot/children")
        }
    }
}