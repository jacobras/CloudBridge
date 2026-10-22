package nl.jacobras.cloudbridge.service.onedrive

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

        assertFailure { service.getUserInfo() }.isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://graph.microsoft.com/oidc/userinfo")
        }
    }

    @Test
    fun `downloading missing file throws NotFoundException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"itemNotFound","message":"Item not found","innerError":{"date":"2026-10-22T13:40:39","request-id":"a1814fdd-d01d-434e-8d29-ff3f21767150","client-request-id":"a1814fdd-d01d-434e-8d29-ff3f21767150"}}}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.downloadFile(CloudItemId("33488461260C4A76!s5f2c8e1a9b7d4c3e8f6a2b1d0c9e7f4a")) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
            .hasMessage("The requested item '33488461260C4A76!s5f2c8e1a9b7d4c3e8f6a2b1d0c9e7f4a' could not be found")
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo(
                "https://graph.microsoft.com/v1.0/me/drive/items/33488461260C4A76!s5f2c8e1a9b7d4c3e8f6a2b1d0c9e7f4a/content"
            )
        }
    }

    @Test
    fun `uploading without space left throws InsufficientSpaceException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"quotaLimitReached","message":"Insufficient Space Available","innerError":{"date":"2026-10-22T14:37:00","request-id":"b5c1f0e2-7a3d-4e9b-8c61-2f4d9a0e3b17","client-request-id":"b5c1f0e2-7a3d-4e9b-8c61-2f4d9a0e3b17"}}}""",
                status = HttpStatusCode.InsufficientStorage,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure {
            service.updateFile(
                CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"),
                "content"
            )
        }
            .isInstanceOf<CloudServiceException.InsufficientSpaceException>()
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Put)
            assertThat(it.url.toString()).isEqualTo(
                "https://graph.microsoft.com/v1.0/me/drive/items/33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc/content"
            )
        }
    }

    @Test
    fun `create file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "createdDateTime": "2025-10-11T16:13:38Z",
                      "eTag": "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},1\"",
                      "id": "33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc",
                      "lastModifiedDateTime": "2025-10-11T16:13:38Z",
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
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.createFile("asdf.txt".asFilePath(), "a")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"),
                path = "asdf.txt".asFilePath(),
                name = "asdf.txt",
                sizeInBytes = 1,
                modified = Instant.parse("2025-10-11T16:13:38Z"),
                revision = "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},1\""
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Put)
        }
    }

    @Test
    fun `create folder`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "createdDateTime": "2025-10-11T15:22:39Z",
                      "eTag": "\"{C4581CC0-F018-449D-9F07-FB9CF832ADAB},1\"",
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
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.createFolder("Work".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("33488461260C4A76!sc4581cc0f018449d9f07fb9cf832adab"),
                path = "Work".asFolderPath(),
                name = "Work"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo("https://graph.microsoft.com/v1.0/me/drive/special/approot/children")
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
                          "eTag": "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},2\"",
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
                modified = Instant.parse("2025-10-11T16:32:05Z"),
                revision = "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},2\""
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://graph.microsoft.com/v1.0/me/drive/special/approot/children")
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
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        service.delete(CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"))

        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Delete)
            assertThat(it.url.toString()).isEqualTo(
                "https://graph.microsoft.com/v1.0/me/drive/items/33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"
            )
        }
    }
}