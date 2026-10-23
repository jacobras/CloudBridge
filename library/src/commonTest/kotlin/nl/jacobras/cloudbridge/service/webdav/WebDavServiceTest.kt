package nl.jacobras.cloudbridge.service.webdav

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
import nl.jacobras.cloudbridge.bufferOf
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
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
                revision = "\"abc123\""
            )
        )
        assertThat(engine.requestHistory.map { it.method }).containsExactly(
            HttpMethod.Put,
            HttpMethod("PROPFIND")
        )
        assertThat(engine.requestHistory.last().headers["Depth"]).isEqualTo("0")
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
                content = "asdf",
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

        assertThat(content).isEqualTo("asdf")
        assertThat(engine.requestHistory).single().given {
            assertThat(it.method).isEqualTo(HttpMethod.Get)
            assertThat(it.url.toString()).isEqualTo("https://webdav.example.com/server.php/Notes%20for%20Android/B/in%20B.txt")
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