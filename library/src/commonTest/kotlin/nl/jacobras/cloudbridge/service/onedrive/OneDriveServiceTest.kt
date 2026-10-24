package nl.jacobras.cloudbridge.service.onedrive

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
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
    fun `download binary file`() = runTest {
        val bytes = examplePngBytes()
        val engine = MockEngine {
            respond(
                content = bytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val sink = Buffer()
        val id = CloudItemId("33488461260C4A76!s5f2c8e1a9b7d4c3e8f6a2b1d0c9e7f4a")
        service.downloadFile(id, sink)

        assertThat(sink.readByteArray().toList()).isEqualTo(bytes.toList())
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://graph.microsoft.com/v1.0/me/drive/items/33488461260C4A76!s5f2c8e1a9b7d4c3e8f6a2b1d0c9e7f4a/content")
        }
    }

    @Test
    fun `downloading missing binary file throws NotFoundException`() = runTest {
        val engine = MockEngine {
            respond(
                content = NOT_FOUND_RESPONSE,
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.downloadFile(CloudItemId("missing"), Buffer()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
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
                      "name": "Welcome.txt",
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

        val file = service.createFile("Welcome.txt".asFilePath(), "a")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"),
                path = "Welcome.txt".asFilePath(),
                name = "Welcome.txt",
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
    fun `update file`() = runTest {
        val engine = MockEngine { request ->
            val content = if (request.method == HttpMethod.Put) {
                """
                    {
                      "eTag": "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},2\"",
                      "id": "33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc",
                      "lastModifiedDateTime": "2025-10-12T10:00:00Z",
                      "name": "Welcome.txt",
                      "size": 2,
                      "parentReference": {
                        "id": "33488461260C4A76!s1234",
                        "path": "/drive/root:/Apps/CloudBridge%20Demo/My%20Notes"
                      }
                    }
                """.trimIndent()
            } else {
                """
                    {
                      "id": "33488461260C4A76!s917d5fe70450423e896e4f9ab0023434",
                      "name": "CloudBridge Demo",
                      "lastModifiedDateTime": "2025-10-11T16:13:38Z",
                      "parentReference": {
                        "id": "33488461260C4A76!sroot",
                        "path": "/drive/root:/Apps"
                      }
                    }
                """.trimIndent()
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.updateFile(
            CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"),
            "ab"
        )
        service.updateFile(CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"), "ab")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"),
                path = "/My Notes/Welcome.txt".asFilePath(),
                name = "Welcome.txt",
                sizeInBytes = 2,
                modified = Instant.parse("2025-10-12T10:00:00Z"),
                revision = "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},2\""
            )
        )
        // The app root is fetched once, then cached
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Put,
            HttpMethod.Get,
            HttpMethod.Put
        )
        assertThat(engine.requestHistory[1].url.toString())
            .isEqualTo("https://graph.microsoft.com/v1.0/me/drive/special/approot")
    }

    @Test
    fun `move file`() = runTest {
        val engine = MockEngine { request ->
            val content = when {
                request.method == HttpMethod.Get && !request.url.encodedPath.endsWith("approot") ->
                    """{"id": "notesFolderId", "name": "Notes", "lastModifiedDateTime": "2025-10-11T16:13:38Z", "parentReference": {"path": "/drive/root:/Apps/CloudBridge%20Demo"}, "folder": {"childCount": 1}}"""
                request.method == HttpMethod.Get -> """
                    {
                      "id": "33488461260C4A76!s917d5fe70450423e896e4f9ab0023434",
                      "name": "CloudBridge Demo",
                      "parentReference": { "path": "/drive/root:/Apps" }
                    }
                """.trimIndent()
                else -> """
                    {
                      "eTag": "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},3\"",
                      "id": "file1",
                      "lastModifiedDateTime": "2025-10-12T10:00:00Z",
                      "name": "new.txt",
                      "size": 2,
                      "parentReference": { "path": "/drive/root:/Apps/CloudBridge%20Demo/Notes" }
                    }
                """.trimIndent()
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.moveFile(CloudItemId("file1"), "/Notes/new.txt".asFilePath())

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("file1"),
                path = "/Notes/new.txt".asFilePath(),
                name = "new.txt",
                sizeInBytes = 2,
                modified = Instant.parse("2025-10-12T10:00:00Z"),
                revision = "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},3\""
            )
        )
        // The destination folder is looked up by path, the app root is fetched to resolve the new path
        assertThat(engine.requestHistory.map { "${it.method.value} ${it.url.encodedPath}" }).containsExactly(
            "GET /v1.0/me/drive/special/approot:/Notes",
            "PATCH /v1.0/me/drive/items/file1",
            "GET /v1.0/me/drive/special/approot"
        )
        assertThat(engine.requestHistory[1].body.toByteArray().decodeToString())
            .isEqualTo("""{"name":"new.txt","parentReference":{"id":"notesFolderId"}}""")
    }

    @Test
    fun `move folder`() = runTest {
        val engine = MockEngine { request ->
            val content = when (request.method) {
                HttpMethod.Get -> """
                    {
                      "id": "33488461260C4A76!s917d5fe70450423e896e4f9ab0023434",
                      "name": "CloudBridge Demo",
                      "parentReference": { "path": "/drive/root:/Apps" }
                    }
                """.trimIndent()
                else -> """
                    {
                      "id": "folder1",
                      "name": "New folder",
                      "lastModifiedDateTime": "2025-10-12T10:00:00Z",
                      "parentReference": { "path": "/drive/root:/Apps/CloudBridge%20Demo" },
                      "folder": { "childCount": 0 }
                    }
                """.trimIndent()
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.moveFolder(CloudItemId("folder1"), "/New folder".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("folder1"),
                path = "/New folder".asFolderPath(),
                name = "New folder"
            )
        )
        // Moving to the root uses the app root as the destination folder
        assertThat(engine.requestHistory.map { "${it.method.value} ${it.url.encodedPath}" }).containsExactly(
            "GET /v1.0/me/drive/special/approot",
            "PATCH /v1.0/me/drive/items/folder1"
        )
        assertThat(engine.requestHistory[1].body.toByteArray().decodeToString())
            .isEqualTo("""{"name":"New folder","parentReference":{"id":"33488461260C4A76!s917d5fe70450423e896e4f9ab0023434"}}""")
    }

    @Test
    fun `moving to an existing path throws ConflictException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"nameAlreadyExists","message":"Name already exists"}}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.moveFile(CloudItemId("file1"), "/Notes/taken.txt".asFilePath()) }
            .isInstanceOf<CloudServiceException.ConflictException>()
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
    fun `create nested folder`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "id": "abc",
                      "lastModifiedDateTime": "2025-10-11T15:22:39Z",
                      "name": "Sub",
                      "size": 0,
                      "parentReference": {
                        "id": "def",
                        "path": "/drive/root:/Apps/CloudBridge Demo/Work"
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

        val folder = service.createFolder("/Work/Sub".asFolderPath())

        assertThat(folder.path).isEqualTo("/Work/Sub".asFolderPath())
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString())
                .isEqualTo("https://graph.microsoft.com/v1.0/me/drive/special/approot:/Work:/children")
            assertThat(it.body.toByteArray().decodeToString()).contains(""""name":"Sub"""")
        }
    }

    @Test
    fun `small binary file uploads in a single request`() = runTest {
        val engine = MockEngine {
            respond(
                content = DRIVE_ITEM_RESPONSE,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.createFile("image.png".asFilePath(), bufferOf(3), mimeType = "image/png")

        assertThat(file.id).isEqualTo(CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"))
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Put)
            assertThat(it.url.toString())
                .isEqualTo("https://graph.microsoft.com/v1.0/me/drive/special/approot:/image.png:/content")
            assertThat(it.body.contentType.toString()).isEqualTo("image/png")
            assertThat(it.body.toByteArray().toList()).isEqualTo(listOf<Byte>(0, 1, 2))
        }
    }

    @Test
    fun `large binary file without size throws IllegalArgumentException`() = runTest {
        val engine = MockEngine { error("No request expected") }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )
        service.uploadChunkSize = 4

        assertFailure { service.createFile("archive.zip".asFilePath(), bufferOf(10)) }
            .isInstanceOf<IllegalArgumentException>()
        assertThat(engine.requestHistory).isEmpty()
    }

    @Test
    fun `large binary file uploads in a session`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("createUploadSession") -> respond(
                    content = """{"uploadUrl":"https://my.microsoftpersonalcontent.com/up/session1"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                request.headers[HttpHeaders.ContentRange]?.endsWith("9/10") == true -> respond(
                    content = DRIVE_ITEM_RESPONSE,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond(
                    content = """{"nextExpectedRanges":["4-"]}""",
                    status = HttpStatusCode.Accepted,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )
        service.uploadChunkSize = 4

        val file = service.createFile("archive.zip".asFilePath(), bufferOf(10), sizeInBytes = 10)

        assertThat(file.id).isEqualTo(CloudItemId("33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc"))
        assertThat(engine.requestHistory.map { "${it.method.value} ${it.url}" }).containsExactly(
            "POST https://graph.microsoft.com/v1.0/me/drive/special/approot:/archive.zip:/createUploadSession",
            "PUT https://my.microsoftpersonalcontent.com/up/session1",
            "PUT https://my.microsoftpersonalcontent.com/up/session1",
            "PUT https://my.microsoftpersonalcontent.com/up/session1"
        )
        val chunkRequests = engine.requestHistory.drop(1)
        assertThat(chunkRequests.map { it.headers[HttpHeaders.ContentRange] }).containsExactly(
            "bytes 0-3/10",
            "bytes 4-7/10",
            "bytes 8-9/10"
        )
        assertThat(chunkRequests.map { it.headers[HttpHeaders.Authorization] }).containsExactly(
            null,
            null,
            null
        )
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
                          "name": "Welcome.txt",
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
                path = "Welcome.txt".asFilePath(),
                name = "Welcome.txt",
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
    fun `get file info`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "eTag": "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},1\"",
                      "id": "file1",
                      "lastModifiedDateTime": "2025-10-11T16:13:38Z",
                      "name": "Welcome.txt",
                      "size": 1,
                      "parentReference": { "path": "/drive/root:/Apps/CloudBridge%20Demo/Notes" }
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

        val file = service.getFileInfo("/Notes/Welcome.txt".asFilePath())

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("file1"),
                path = "/Notes/Welcome.txt".asFilePath(),
                name = "Welcome.txt",
                sizeInBytes = 1,
                modified = Instant.parse("2025-10-11T16:13:38Z"),
                revision = "\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},1\""
            )
        )
        assertThat(engine.requestHistory.single().url.encodedPath)
            .isEqualTo("/v1.0/me/drive/special/approot:/Notes/Welcome.txt")
    }

    @Test
    fun `get file info throws NotFoundException when missing`() = runTest {
        val engine = MockEngine {
            respond(
                content = NOT_FOUND_RESPONSE,
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = OneDriveService(
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
                content = ONEDRIVE_FOLDER_JSON,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = OneDriveService(
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
                content = ONEDRIVE_FOLDER_JSON,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.getFolderInfo("/Notes".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("folder1"),
                path = "/Notes".asFolderPath(),
                name = "Notes"
            )
        )
        assertThat(engine.requestHistory.single().url.encodedPath)
            .isEqualTo("/v1.0/me/drive/special/approot:/Notes")
    }

    @Test
    fun `get folder info throws NotFoundException for a file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"id": "file1", "name": "Welcome.txt", "size": 1, "eTag": "1", "lastModifiedDateTime": "2025-10-11T16:13:38Z", "parentReference": { "path": "/drive/root:/Apps/CloudBridge%20Demo" }}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFolderInfo("/Welcome.txt".asFolderPath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
    }

    @Test
    fun `get folder info for root throws IllegalArgumentException`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.OK) }
        val service = OneDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFolderInfo("/".asFolderPath()) }
            .isInstanceOf<IllegalArgumentException>()
        assertThat(engine.requestHistory).isEmpty()
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

private const val ONEDRIVE_FOLDER_JSON =
    """{"id":"folder1","name":"Notes","lastModifiedDateTime":"2025-10-11T16:13:38Z","parentReference":{"path":"/drive/root:/Apps/CloudBridge%20Demo"},"folder":{"childCount":1}}"""

private const val DRIVE_ITEM_RESPONSE =
    """{"id":"33488461260C4A76!s361fe060efd641f586d9b4af4478f0dc","eTag":"\"{361FE060-EFD6-41F5-86D9-B4AF4478F0DC},1\"","lastModifiedDateTime":"2025-10-11T16:13:38Z","name":"archive.zip","size":10,"parentReference":{"id":"33488461260C4A76!s917d5fe70450423e896e4f9ab0023434","path":"/drive/root:/Apps/CloudBridge Demo"},"file":{"mimeType":"application/zip"}}"""

private const val NOT_FOUND_RESPONSE =
    """{"error":{"code":"itemNotFound","message":"Item not found"}}"""