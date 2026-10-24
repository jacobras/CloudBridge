package nl.jacobras.cloudbridge.service.googledrive

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.single
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
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
    fun `download binary file`() = runTest {
        val bytes = examplePngBytes()
        val engine = MockEngine {
            respond(
                content = bytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val sink = Buffer()
        val id = CloudItemId("1mVq7Rk2XwTz9LpHc4Ns8DfJy3Gb6Ue5Ao0Ki1WrQxSdZtYhPg")
        service.downloadFile(id, sink)

        assertThat(sink.readByteArray().toList()).isEqualTo(bytes.toList())
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://www.googleapis.com/drive/v3/files/1mVq7Rk2XwTz9LpHc4Ns8DfJy3Gb6Ue5Ao0Ki1WrQxSdZtYhPg?alt=media")
        }
    }

    @Test
    fun `downloading missing binary file throws NotFoundException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":404,"message":"File not found: missing.","errors":[{"message":"File not found: missing.","domain":"global","reason":"notFound","location":"fileId","locationType":"parameter"}]}}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = GoogleDriveService(
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
    fun `appDataFolder id is fetched again after switching accounts`() = runTest {
        val engine = MockEngine { request ->
            val rootId = if (request.headers[HttpHeaders.Authorization] == "Bearer first") "rootA" else "rootB"
            val content = if (request.method == HttpMethod.Patch) {
                """
                    {
                      "mimeType": "text/plain",
                      "parents": ["$rootId"],
                      "size": "8",
                      "id": "file1",
                      "name": "hello.txt",
                      "trashed": false,
                      "modifiedTime": "2025-10-12T10:00:00.000Z",
                      "version": "2"
                    }
                """.trimIndent()
            } else {
                """{"id": "$rootId"}"""
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("first"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        service.updateFile(CloudItemId("file1"), "Updated!")
        service.setToken(CloudAccessToken("second"))
        val file = service.updateFile(CloudItemId("file1"), "Updated!")

        assertThat(file.path).isEqualTo("/hello.txt".asFilePath())
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Patch,
            HttpMethod.Get,
            HttpMethod.Patch,
            HttpMethod.Get
        )
    }

    @Test
    fun `create file`() = runTest {
        val engine = MockEngine { request ->
            if (request.isNameCheck()) return@MockEngine respondFiles()
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
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Get,
            HttpMethod.Post
        )
        assertThat(engine.requestHistory.first().url.parameters["q"])
            .isEqualTo("name = 'hello.txt' and 'appDataFolder' in parents and trashed = false")
        engine.requestHistory.last().let {
            assertThat(it.url.toString()).isEqualTo(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart" +
                        "&fields=id%2Cname%2CmimeType%2Csize%2CmodifiedTime%2Cparents%2Cversion%2Ctrashed"
            )
        }
    }

    @Test
    fun `create folder`() = runTest {
        val engine = MockEngine { request ->
            if (request.isNameCheck()) return@MockEngine respondFiles()
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
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Get,
            HttpMethod.Post
        )
        engine.requestHistory.last().let {
            assertThat(it.url.toString()).isEqualTo(
                "https://www.googleapis.com/drive/v3/files" +
                        "?fields=id%2Cname%2CmimeType%2Csize%2CmodifiedTime%2Cparents%2Cversion%2Ctrashed"
            )
        }
    }

    @Test
    fun `create nested folder`() = runTest {
        val engine = MockEngine { request ->
            if (request.isNameCheck()) return@MockEngine respondFiles()
            respond(
                content = """
                    {
                      "mimeType": "application/vnd.google-apps.folder",
                      "parents": [
                        "parentId"
                      ],
                      "id": "subId",
                      "name": "Sub",
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

        val folder = service.createFolder("/parentId/Sub".asFolderPath())

        assertThat(folder.path).isEqualTo("/parentId/subId".asFolderPath())
        assertThat(engine.requestHistory.first().url.parameters["q"])
            .isEqualTo("name = 'Sub' and 'parentId' in parents and trashed = false")
        assertThat(engine.requestHistory.last().body.toByteArray().decodeToString())
            .contains(""""parents":["parentId"]""")
    }

    @Test
    fun `creating file or folder with existing name throws ConflictException`() = runTest {
        val engine = MockEngine {
            respondFiles(EXISTING_FILE_JSON)
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.createFile("/hello.txt".asFilePath(), "Hello") }
            .isInstanceOf<CloudServiceException.ConflictException>()
        assertFailure { service.createFile("/hello.txt".asFilePath(), bufferOf(3)) }
            .isInstanceOf<CloudServiceException.ConflictException>()
        assertFailure { service.createFolder("/hello.txt".asFolderPath()) }
            .isInstanceOf<CloudServiceException.ConflictException>()

        // Only the name checks, nothing was created
        assertThat(engine.requestHistory.map { it.method })
            .containsExactly(HttpMethod.Get, HttpMethod.Get, HttpMethod.Get)
    }

    @Test
    fun `rename file within same folder`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.isNameCheck() -> respondFiles()
                request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/appDataFolder") ->
                    respondJson("""{"id": "1THG-A2vXis2oKtcHO7fK6u_1VLRN"}""")
                request.method == HttpMethod.Get -> respondJson(EXISTING_FILE_JSON)
                else -> respondJson(EXISTING_FILE_JSON.replace("hello.txt", "it's.txt"))
            }
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.moveFile(
            CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
            "/it's.txt".asFilePath()
        )

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
                path = "/it's.txt".asFilePath(),
                name = "it's.txt",
                sizeInBytes = 10,
                modified = Instant.parse("2025-10-11T16:31:27.438Z"),
                revision = "1"
            )
        )
        assertThat(engine.requestHistory.map { "${it.method.value} ${it.url.encodedPath}" }).containsExactly(
            "GET /drive/v3/files/1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg",
            "GET /drive/v3/files",
            "GET /drive/v3/files/appDataFolder",
            "PATCH /drive/v3/files/1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"
        )
        assertThat(engine.requestHistory[1].url.parameters["q"])
            .isEqualTo("name = 'it\\'s.txt' and 'appDataFolder' in parents and trashed = false")
        engine.requestHistory[3].let {
            assertThat(it.body.toByteArray().decodeToString()).isEqualTo("""{"name":"it's.txt"}""")
            // Parent didn't change, so it's not touched
            assertThat(it.url.parameters["addParents"]).isNull()
            assertThat(it.url.parameters["removeParents"]).isNull()
        }
    }

    @Test
    fun `move file into another folder`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.isNameCheck() -> respondFiles()
                request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/appDataFolder") ->
                    respondJson("""{"id": "1THG-A2vXis2oKtcHO7fK6u_1VLRN"}""")
                request.method == HttpMethod.Get -> respondJson(EXISTING_FILE_JSON)
                else -> respondJson(
                    EXISTING_FILE_JSON.replace(
                        "1THG-A2vXis2oKtcHO7fK6u_1VLRN",
                        "folderX"
                    )
                )
            }
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.moveFile(
            CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
            "/folderX/hello.txt".asFilePath()
        )

        assertThat(file.path).isEqualTo("/folderX/hello.txt".asFilePath())
        val patch = engine.requestHistory.single { it.method == HttpMethod.Patch }
        assertThat(patch.url.parameters["addParents"]).isEqualTo("folderX")
        assertThat(patch.url.parameters["removeParents"]).isEqualTo("1THG-A2vXis2oKtcHO7fK6u_1VLRN")
        assertThat(engine.requestHistory[1].url.parameters["q"])
            .isEqualTo("name = 'hello.txt' and 'folderX' in parents and trashed = false")
    }

    @Test
    fun `rename folder`() = runTest {
        val folderJson = """
            {
              "mimeType": "application/vnd.google-apps.folder",
              "parents": ["1THG-A2vXis2oKtcHO7fK6u_1VLRN"],
              "id": "131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA",
              "name": "Private",
              "trashed": false,
              "modifiedTime": "2025-10-11T15:22:38.925Z",
              "version": "2"
            }
        """.trimIndent()
        val engine = MockEngine { request ->
            when {
                request.isNameCheck() -> respondFiles()
                request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/appDataFolder") ->
                    respondJson("""{"id": "1THG-A2vXis2oKtcHO7fK6u_1VLRN"}""")
                else -> respondJson(folderJson)
            }
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.moveFolder(
            CloudItemId("131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA"),
            "/Private".asFolderPath()
        )

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA"),
                path = "/131oVBv55ScZRUNJiUft_pihQWdnJP3FoTM4kgLpgW52VJqkHbA".asFolderPath(),
                name = "Private"
            )
        )
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Get,
            HttpMethod.Get,
            HttpMethod.Get,
            HttpMethod.Patch
        )
    }

    @Test
    fun `moving to an existing path throws ConflictException`() = runTest {
        val engine = MockEngine { request ->
            if (request.isNameCheck()) {
                respondFiles(EXISTING_FILE_JSON.replace("1BZWMYkXl", "other"))
            } else {
                respondJson(EXISTING_FILE_JSON)
            }
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure {
            service.moveFile(
                CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
                "/hello.txt".asFilePath()
            )
        }.isInstanceOf<CloudServiceException.ConflictException>()
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Get,
            HttpMethod.Get
        )
    }

    @Test
    fun `moving to the current path is allowed`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.isNameCheck() -> respondFiles(EXISTING_FILE_JSON)
                request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/appDataFolder") ->
                    respondJson("""{"id": "1THG-A2vXis2oKtcHO7fK6u_1VLRN"}""")
                else -> respondJson(EXISTING_FILE_JSON)
            }
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        service.moveFile(
            CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
            "/hello.txt".asFilePath()
        )

        assertThat(engine.requestHistory.map { it.method }).contains(HttpMethod.Patch)
    }

    @Test
    fun `moving a folder to the root throws IllegalArgumentException`() = runTest {
        val engine = MockEngine { respondJson(EXISTING_FILE_JSON) }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.moveFolder(CloudItemId("1BZWMYkXl"), "/".asFolderPath()) }
            .isInstanceOf<IllegalArgumentException>()
        assertThat(engine.requestHistory).isEmpty()
    }

    @Test
    fun `large binary file uploads in a resumable session`() = runTest {
        val sessionUri =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=session1"
        val engine = MockEngine { request ->
            when {
                request.isNameCheck() -> respondFiles()
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
        val (nameCheck, uploadRequests) = engine.requestHistory.first() to engine.requestHistory.drop(
            1
        )
        assertThat(nameCheck.method).isEqualTo(HttpMethod.Get)
        val (start, chunks) = uploadRequests.first() to uploadRequests.drop(1)
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
    fun `get file info`() = runTest {
        val engine = MockEngine {
            // A folder with the same name is skipped
            respondFiles(
                EXISTING_FILE_JSON.replace("text/plain", "application/vnd.google-apps.folder")
                    .replace("1BZWMYkXl", "folder"),
                EXISTING_FILE_JSON
            )
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.getFileInfo("/1THG-A2vXis2oKtcHO7fK6u_1VLRN/hello.txt".asFilePath())

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg"),
                path = "/1THG-A2vXis2oKtcHO7fK6u_1VLRN/hello.txt".asFilePath(),
                name = "hello.txt",
                sizeInBytes = 10,
                modified = Instant.parse("2025-10-11T16:31:27.438Z"),
                revision = "1"
            )
        )
        assertThat(engine.requestHistory.single().url.parameters["q"])
            .isEqualTo("name = 'hello.txt' and '1THG-A2vXis2oKtcHO7fK6u_1VLRN' in parents and trashed = false")
    }

    @Test
    fun `get file info throws NotFoundException when missing`() = runTest {
        val engine = MockEngine { respondFiles() }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFileInfo("/it's.txt".asFilePath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
        assertThat(engine.requestHistory.single().url.parameters["q"])
            .isEqualTo("name = 'it\\'s.txt' and 'appDataFolder' in parents and trashed = false")
    }

    @Test
    fun `get folder info`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/appDataFolder")) {
                respondJson("""{"id": "1THG-A2vXis2oKtcHO7fK6u_1VLRN"}""")
            } else {
                respondJson(
                    EXISTING_FILE_JSON.replace("text/plain", "application/vnd.google-apps.folder")
                        .replace("1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg", "folder1")
                        .replace("hello.txt", "Work")
                )
            }
        }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder = service.getFolderInfo("/folder1".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("folder1"),
                path = "/folder1".asFolderPath(),
                name = "Work"
            )
        )
        assertThat(engine.requestHistory.map { it.url.encodedPath }).containsExactly(
            "/drive/v3/files/folder1",
            "/drive/v3/files/appDataFolder"
        )
    }

    @Test
    fun `get folder info for root throws IllegalArgumentException`() = runTest {
        val engine = MockEngine { respondJson(EXISTING_FILE_JSON) }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFolderInfo("/".asFolderPath()) }
            .isInstanceOf<IllegalArgumentException>()
        assertThat(engine.requestHistory).isEmpty()
    }

    @Test
    fun `get folder info throws NotFoundException for a file`() = runTest {
        val engine = MockEngine { respondJson(EXISTING_FILE_JSON) }
        val service = GoogleDriveService(
            token = CloudAccessToken("valid"),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.getFolderInfo("/1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg".asFolderPath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
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

private val EXISTING_FILE_JSON = """
    {
      "mimeType": "text/plain",
      "parents": ["1THG-A2vXis2oKtcHO7fK6u_1VLRN"],
      "size": "10",
      "id": "1BZWMYkXl-wYxRa-R1VzoJAmGyPrpO9zpxBeOHeb272OJ2hslPg",
      "name": "hello.txt",
      "trashed": false,
      "modifiedTime": "2025-10-11T16:31:27.438Z",
      "version": "1"
    }
""".trimIndent()

/**
 * Whether this is the duplicate name check done before creating or renaming.
 */
private fun HttpRequestData.isNameCheck(): Boolean {
    return method == HttpMethod.Get && url.parameters["q"]?.startsWith("name = ") == true
}

private fun MockRequestHandleScope.respondFiles(vararg files: String) = respondJson(
    """{"files": [${files.joinToString()}]}"""
)

private fun MockRequestHandleScope.respondJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
)