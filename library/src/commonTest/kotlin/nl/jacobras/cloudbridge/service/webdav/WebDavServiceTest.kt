package nl.jacobras.cloudbridge.service.webdav

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.single
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
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
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import nl.jacobras.cloudbridge.util.bufferOf
import nl.jacobras.cloudbridge.util.examplePngBytes
import nl.jacobras.cloudbridge.util.failingSink
import kotlin.test.Test
import kotlin.time.Instant

class WebDavServiceTest {

    @Test
    fun `uploading without space left throws InsufficientSpaceException`() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.InsufficientStorage
            )
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.createFile("/Notes for Android/big.txt".asFilePath(), "content") }
            .isInstanceOf<CloudServiceException.InsufficientSpaceException>()
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Put)
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php/Notes%20for%20Android/big.txt")
        }
    }

    @Test
    fun `create file returns CloudFile`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Put) {
                respond(content = "", status = HttpStatusCode.Created)
            } else {
                respond(
                    content = """
                        <?xml version="1.0"?>
                        <d:multistatus xmlns:d="DAV:"><d:response><d:href>/server.php/Notes%20for%20Android/note.txt</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>5</d:getcontentlength><d:getlastmodified>Tue, 02 Apr 2024 18:27:06 GMT</d:getlastmodified><d:displayname>note.txt</d:displayname><d:getetag>"abc123"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
                    """.trimIndent(),
                    status = HttpStatusCode.MultiStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                )
            }
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.createFile("/Notes for Android/note.txt".asFilePath(), "hello")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("/Notes for Android/note.txt"),
                path = "/Notes for Android/note.txt".asFilePath(),
                name = "note.txt",
                sizeInBytes = 5,
                modified = Instant.parse("2024-04-02T18:27:06Z"),
                revision = "abc123"
            )
        )
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Put,
            HttpMethod("PROPFIND")
        )
        assertThat(engine.requestHistory.last().headers["Depth"]).isEqualTo("0")
    }

    @Test
    fun `update file`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Put) {
                respond(content = "", status = HttpStatusCode.NoContent)
            } else {
                respond(
                    content = """
                        <?xml version="1.0"?>
                        <d:multistatus xmlns:d="DAV:"><d:response><d:href>/server.php/Notes%20for%20Android/note.txt</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>7</d:getcontentlength><d:getlastmodified>Wed, 03 Apr 2024 10:00:00 GMT</d:getlastmodified><d:displayname>note.txt</d:displayname><d:getetag>"def456"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
                    """.trimIndent(),
                    status = HttpStatusCode.MultiStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                )
            }
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.updateFile(CloudItemId("/Notes for Android/note.txt"), "updated")

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("/Notes for Android/note.txt"),
                path = "/Notes for Android/note.txt".asFilePath(),
                name = "note.txt",
                sizeInBytes = 7,
                modified = Instant.parse("2024-04-03T10:00:00Z"),
                revision = "def456"
            )
        )
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Put,
            HttpMethod("PROPFIND")
        )
        assertThat(engine.requestHistory.first().body.contentType.toString()).isEqualTo("text/plain; charset=UTF-8")
    }

    @Test
    fun `move file`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod("MOVE")) {
                respond(content = "", status = HttpStatusCode.Created)
            } else {
                respond(
                    content = """
                        <?xml version="1.0"?>
                        <d:multistatus xmlns:d="DAV:"><d:response><d:href>/server.php/Archive/new%20name.txt</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>5</d:getcontentlength><d:getlastmodified>Tue, 02 Apr 2024 18:27:06 GMT</d:getlastmodified><d:displayname>new name.txt</d:displayname><d:getetag>"abc123"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
                    """.trimIndent(),
                    status = HttpStatusCode.MultiStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                )
            }
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.moveFile(
            CloudItemId("/Notes for Android/note.txt"),
            "/Archive/new name.txt".asFilePath()
        )

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("/Archive/new name.txt"),
                path = "/Archive/new name.txt".asFilePath(),
                name = "new name.txt",
                sizeInBytes = 5,
                modified = Instant.parse("2024-04-02T18:27:06Z"),
                revision = "abc123"
            )
        )
        engine.requestHistory.first().let {
            assertThat(it.method).isEqualTo(HttpMethod("MOVE"))
            assertThat(it.url.toString())
                .isEqualTo("https://webdav.example.com/server.php/Notes%20for%20Android/note.txt")
            assertThat(it.headers["Destination"])
                .isEqualTo("https://webdav.example.com/server.php/Archive/new%20name.txt")
            assertThat(it.headers["Overwrite"]).isEqualTo("F")
        }
        assertThat(engine.requestHistory.last().url.toString())
            .isEqualTo("https://webdav.example.com/server.php/Archive/new%20name.txt")
    }

    @Test
    fun `move folder`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod("MOVE")) {
                respond(content = "", status = HttpStatusCode.Created)
            } else {
                respond(
                    content = """
                        <?xml version="1.0"?>
                        <d:multistatus xmlns:d="DAV:"><d:response><d:href>/server.php/Archive/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getlastmodified>Tue, 02 Apr 2024 18:27:06 GMT</d:getlastmodified></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
                    """.trimIndent(),
                    status = HttpStatusCode.MultiStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                )
            }
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val folder =
            service.moveFolder(CloudItemId("/Notes for Android/"), "/Archive".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("/Archive/"),
                path = "/Archive".asFolderPath(),
                name = "Archive"
            )
        )
        engine.requestHistory.first().let {
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php/Notes%20for%20Android/")
            assertThat(it.headers["Destination"]).isEqualTo("https://webdav.example.com/server.php/Archive/")
        }
    }

    @Test
    fun `moving to an existing path throws ConflictException`() = runTest {
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.PreconditionFailed)
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.moveFile(CloudItemId("/note.txt"), "/taken.txt".asFilePath()) }
            .isInstanceOf<CloudServiceException.ConflictException>()
        assertThat(engine.requestHistory).single()
    }

    @Test
    fun `create binary file streams content`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Put) {
                respond(content = "", status = HttpStatusCode.Created)
            } else {
                respond(
                    content = """
                        <?xml version="1.0"?>
                        <d:multistatus xmlns:d="DAV:"><d:response><d:href>/server.php/image.png</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>3</d:getcontentlength><d:getlastmodified>Tue, 02 Apr 2024 18:27:06 GMT</d:getlastmodified><d:displayname>image.png</d:displayname><d:getetag>"abc123"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
                    """.trimIndent(),
                    status = HttpStatusCode.MultiStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                )
            }
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val file = service.createFile(
            "/image.png".asFilePath(),
            bufferOf(3),
            sizeInBytes = 3,
            mimeType = "image/png"
        )

        assertThat(file.sizeInBytes).isEqualTo(3)
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Put,
            HttpMethod("PROPFIND")
        )
        engine.requestHistory.first().let {
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php/image.png")
            assertThat(it.body.contentType.toString()).isEqualTo("image/png")
            assertThat(it.body.contentLength).isEqualTo(3)
            assertThat(it.body.toByteArray().toList()).isEqualTo(listOf<Byte>(0, 1, 2))
        }
    }

    @Test
    fun `list files in root`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    <?xml version="1.0"?>
                    <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns"><d:response><d:href>/server.php/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getlastmodified>Tue, 02 Apr 2024 18:27:06 GMT</d:getlastmodified></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat><d:propstat><d:prop><d:getcontentlength/><d:displayname/><d:getetag/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response><d:response><d:href>/server.php/Notes%20for%20Android/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getlastmodified>Mon, 29 Apr 2024 22:12:48 GMT</d:getlastmodified></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat><d:propstat><d:prop><d:getcontentlength/><d:displayname/><d:getetag/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response></d:multistatus>
                """.trimIndent(),
                status = HttpStatusCode.MultiStatus,
                headers = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            )
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val items = service.listFiles(FolderPath("/"))

        assertThat(items).containsExactly(
            CloudFolder(
                id = CloudItemId("/Notes for Android/"),
                path = "/Notes for Android".asFolderPath(),
                name = "Notes for Android"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod("PROPFIND"))
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php")
            assertThat(it.headers["Depth"]).isEqualTo("1")
        }
    }

    @Test
    fun `download file`() = runTest {
        val engine = MockEngine {
            respond(
                content = "Hello",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
            )
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val content = service.downloadFile(CloudItemId("/Notes for Android/B/in B.txt"))

        assertThat(content).isEqualTo("Hello")
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php/Notes%20for%20Android/B/in%20B.txt")
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
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        val sink = Buffer()
        service.downloadFile(CloudItemId("/Photos/cat.png"), sink)

        assertThat(sink.readByteArray().toList()).isEqualTo(bytes.toList())
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php/Photos/cat.png")
        }
    }

    @Test
    fun `failing sink write throws SinkWriteException`() = runTest {
        val engine = MockEngine {
            respond(
                content = examplePngBytes(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
            )
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.downloadFile(CloudItemId("/Photos/cat.png"), failingSink()) }
            .isInstanceOf<CloudServiceException.SinkWriteException>()
    }

    @Test
    fun `downloading missing binary file throws NotFoundException`() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        assertFailure { service.downloadFile(CloudItemId("/missing.png"), Buffer()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
    }

    @Test
    fun `get file info`() = runTest {
        val engine = MockEngine { respondMultiStatus(WEBDAV_FILE_RESPONSE) }
        val service = webDavService(engine)

        val file = service.getFileInfo("/Notes for Android/note.txt".asFilePath())

        assertThat(file).isEqualTo(
            CloudFile(
                id = CloudItemId("/Notes for Android/note.txt"),
                path = "/Notes for Android/note.txt".asFilePath(),
                name = "note.txt",
                sizeInBytes = 5,
                modified = Instant.parse("2024-04-02T18:27:06Z"),
                revision = "abc123"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.url.toString())
                .isEqualTo("https://webdav.example.com/server.php/Notes%20for%20Android/note.txt")
            assertThat(it.headers["Depth"]).isEqualTo("0")
        }
    }

    @Test
    fun `get file info throws NotFoundException when missing`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.NotFound) }
        val service = webDavService(engine)

        assertFailure { service.getFileInfo("/missing.txt".asFilePath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
    }

    @Test
    fun `get file info throws NotFoundException for a folder`() = runTest {
        val engine = MockEngine { respondMultiStatus(WEBDAV_FOLDER_RESPONSE) }
        val service = webDavService(engine)

        assertFailure { service.getFileInfo("/Notes for Android".asFilePath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
    }

    @Test
    fun `get folder info`() = runTest {
        val engine = MockEngine { respondMultiStatus(WEBDAV_FOLDER_RESPONSE) }
        val service = webDavService(engine)

        val folder = service.getFolderInfo("/Notes for Android".asFolderPath())

        assertThat(folder).isEqualTo(
            CloudFolder(
                id = CloudItemId("/Notes for Android/"),
                path = "/Notes for Android".asFolderPath(),
                name = "Notes for Android"
            )
        )
        assertThat(engine.requestHistory).single().given {
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php/Notes%20for%20Android/")
            assertThat(it.headers["Depth"]).isEqualTo("0")
        }
    }

    @Test
    fun `get folder info throws NotFoundException for a file`() = runTest {
        val engine = MockEngine { respondMultiStatus(WEBDAV_FILE_RESPONSE) }
        val service = webDavService(engine)

        assertFailure { service.getFolderInfo("/Notes for Android/note.txt".asFolderPath()) }
            .isInstanceOf<CloudServiceException.NotFoundException>()
    }

    @Test
    fun `get folder info for root throws IllegalArgumentException`() = runTest {
        val engine = MockEngine { respondMultiStatus(WEBDAV_FOLDER_RESPONSE) }
        val service = webDavService(engine)

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
        val service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() }
        )

        service.delete(CloudItemId("/Notes for Android/B/in B.txt"))

        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Delete)
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php/Notes%20for%20Android/B/in%20B.txt")
        }
    }
}

private const val WEBDAV_FILE_RESPONSE =
    """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/server.php/Notes%20for%20Android/note.txt</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>5</d:getcontentlength><d:getlastmodified>Tue, 02 Apr 2024 18:27:06 GMT</d:getlastmodified><d:displayname>note.txt</d:displayname><d:getetag>"abc123"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""

private const val WEBDAV_FOLDER_RESPONSE =
    """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/server.php/Notes%20for%20Android/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getlastmodified>Tue, 02 Apr 2024 18:27:06 GMT</d:getlastmodified></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""

private fun webDavService(engine: MockEngine) = WebDavService(
    credentials = WebDavCredentials(
        serverUrl = "https://webdav.example.com/server.php",
        username = "user",
        password = "pass"
    ),
    httpClient = HttpClient(engine) { applyDefaults() }
)

private fun MockRequestHandleScope.respondMultiStatus(content: String) = respond(
    content = content,
    status = HttpStatusCode.MultiStatus,
    headers = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
)