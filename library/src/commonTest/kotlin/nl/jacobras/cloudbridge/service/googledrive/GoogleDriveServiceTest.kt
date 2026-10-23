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
import nl.jacobras.cloudbridge.util.bufferOf
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
    fun `uploading without space left throws InsufficientSpaceException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "error": {
                        "code": 403,
                        "message": "The user's Drive storage quota has been exceeded.",
                        "errors": [
                          {
                            "message": "The user's Drive storage quota has been exceeded.",
                            "domain": "usageLimits",
                            "reason": "storageQuotaExceeded"
                          }
                        ]
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure {
            service.updateFile(
                CloudItemId("1lH3t_0YffDLwRj5pr1LZGu386IJ9XHaCOu3MSxApjShLLUD-fg"),
                "content"
            )
        }
            .isInstanceOf<CloudServiceException.InsufficientSpaceException>()
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Patch)
            assertThat(it.url.toString()).isEqualTo(
                "https://www.googleapis.com/upload/drive/v3/files/1lH3t_0YffDLwRj5pr1LZGu386IJ9XHaCOu3MSxApjShLLUD-fg?uploadType=media" +
                        "&fields=id%2Cname%2CmimeType%2Csize%2CmodifiedTime%2Cparents%2Cversion%2Ctrashed"
            )
        }
    }

    @Test
    fun `update file`() = runTest {
        val engine = MockEngine { request ->
            val content = if (request.method == HttpMethod.Patch) {
                """
                    {
                      "mimeType": "text/plain",
                      "parents": ["1THG-A2vXis2oKtcHO7fK6u_1VLRN"],
                      "size": "8",
                      "id": "1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg",
                      "name": "hello.txt",
                      "trashed": false,
                      "modifiedTime": "2025-10-12T10:00:00.000Z",
                      "version": "2"
                    }
                """.trimIndent()
            } else {
                """{"id": "1THG-A2vXis2oKtcHO7fK6u_1VLRN"}"""
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.updateFile(
            CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
            "Updated!"
        )
        service.updateFile(
            CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
            "Updated!"
        )

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
                path = "/hello.txt".asFilePath(),
                name = "hello.txt",
                sizeInBytes = 8,
                modified = Instant.parse("2025-10-12T10:00:00.000Z"),
                revision = "2"
            )
        )
        // The appDataFolder id is fetched once, then cached
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Patch,
            HttpMethod.Get,
            HttpMethod.Patch
        )
        assertThat(engine.requestHistory[0].body.contentType.toString()).isEqualTo("text/plain")
        assertThat(engine.requestHistory[1].url.toString())
            .isEqualTo("https://www.googleapis.com/drive/v3/files/appDataFolder?fields=id")
    }

    @Test
    fun `create file`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
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
                      "version": "1"
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

        val file = service.createFile("/hello.txt".asFilePath(), "Hello you!")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
                path = "/hello.txt".asFilePath(),
                name = "hello.txt",
                sizeInBytes = 10,
                modified = Instant.parse("2025-10-11T16:31:27.438Z"),
                revision = "1"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart" +
                        "&fields=id%2Cname%2CmimeType%2Csize%2CmodifiedTime%2Cparents%2Cversion%2Ctrashed"
            )
        }
    }

    @Test
    fun `create folder`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
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

        val folder = service.createFolder("/Work".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA"),
                path = "/131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA".asFolderPath(),
                name = "Work"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Post)
            assertThat(it.url.toString()).isEqualTo(
                "https://www.googleapis.com/drive/v3/files" +
                        "?fields=id%2Cname%2CmimeType%2Csize%2CmodifiedTime%2Cparents%2Cversion%2Ctrashed"
            )
        }
    }

    @Test
    fun `large binary file uploads in a resumable session`() = runTest {
        val sessionUri =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=session1"
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Post -> respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.Location, sessionUri)
                )
                request.headers[HttpHeaders.ContentRange]?.endsWith("/10") == true -> respond(
                    content = """
                        {
                          "mimeType": "application/zip",
                          "parents": ["1THG-A2vXis2oKtcHO7fK6u_1VLRN"],
                          "size": "10",
                          "id": "1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg",
                          "name": "archive.zip",
                          "trashed": false,
                          "modifiedTime": "2025-10-11T16:31:27.438Z",
                          "version": "1"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
                )
                else -> respond(
                    content = "",
                    status = HttpStatusCode.PermanentRedirect, // 308 Resume Incomplete
                    headers = headersOf(HttpHeaders.Range, "bytes=0-3")
                )
            }
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )
        service.uploadChunkSize = 4

        val file = service.createFile(
            "/archive.zip".asFilePath(),
            bufferOf(10),
            mimeType = "application/zip"
        )

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
                path = "/archive.zip".asFilePath(),
                name = "archive.zip",
                sizeInBytes = 10,
                modified = Instant.parse("2025-10-11T16:31:27.438Z"),
                revision = "1"
            )
        )
        val (start, chunks) = engine.requestHistory.first() to engine.requestHistory.drop(1)
        assertThat(start.url.toString()).isEqualTo(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable" +
                    "&fields=id%2Cname%2CmimeType%2Csize%2CmodifiedTime%2Cparents%2Cversion%2Ctrashed"
        )
        assertThat(start.headers["X-Upload-Content-Type"]).isEqualTo("application/zip")
        assertThat(start.body.toByteArray().decodeToString())
            .isEqualTo("""{"name":"archive.zip","mimeType":"application/zip","parents":["appDataFolder"]}""")
        assertThat(chunks.map { "${it.method.value} ${it.url}" }).containsExactly(
            "PUT $sessionUri",
            "PUT $sessionUri",
            "PUT $sessionUri"
        )
        assertThat(chunks.map { it.headers[HttpHeaders.ContentRange] }).containsExactly(
            "bytes 0-3/*",
            "bytes 4-7/*",
            "bytes 8-9/10"
        )
    }

    @Test
    fun `failed chunk upload throws CloudServiceException`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.Location,
                        "https://www.googleapis.com/upload/session1"
                    )
                )
            } else {
                respond(
                    content = """{"error":{"code":403,"errors":[{"reason":"storageQuotaExceeded"}]}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
                )
            }
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )
        service.uploadChunkSize = 4

        assertFailure { service.createFile("/archive.zip".asFilePath(), bufferOf(10)) }
            .isInstanceOf<CloudServiceException.InsufficientSpaceException>()
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
                modified = Instant.parse("2025-10-11T16:31:27.438Z"),
                revision = "5"
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