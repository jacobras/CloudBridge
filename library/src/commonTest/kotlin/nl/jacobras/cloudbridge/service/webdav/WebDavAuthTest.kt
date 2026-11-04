@file:OptIn(InternalCloudBridgeApi::class)

package nl.jacobras.cloudbridge.service.webdav

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.startsWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.internal.CloudBridgeInternals.applyDefaults
import nl.jacobras.cloudbridge.internal.InternalCloudBridgeApi
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import nl.jacobras.cloudbridge.util.bufferOf
import kotlin.test.Test

class WebDavAuthTest {

    @Test
    fun `Basic server gets Basic after its challenge, then preemptively`() = runTest {
        val server = FakeServer(Scheme.Basic)

        server.service().downloadFile(CloudItemId("/a.txt"))
        server.service.downloadFile(CloudItemId("/a.txt"))

        assertThat(server.authHeaders()).containsExactly(null, BASIC_HEADER, BASIC_HEADER)
    }

    @Test
    fun `Digest server gets Digest after its challenge, then preemptively with increasing nc`() = runTest {
        val server = FakeServer(Scheme.Digest)

        server.service().downloadFile(CloudItemId("/a.txt"))
        server.service.downloadFile(CloudItemId("/a.txt"))

        val headers = server.authHeaders()
        assertThat(headers).hasSize(3)
        assertThat(headers[0]).isNull()
        assertThat(headers[1]).isNotNull().startsWith("Digest username=\"user\"")
        assertThat(headers[1]!!.param("nc")).isEqualTo("00000001")
        assertThat(headers[1]!!.param("uri")).isEqualTo("/server.php/a.txt")
        assertThat(headers[2]!!.param("nc")).isEqualTo("00000002")
    }

    @Test
    fun `Digest is preferred when the server offers Basic too`() = runTest {
        val server = FakeServer(Scheme.Digest, alsoOfferBasic = true)

        server.service().downloadFile(CloudItemId("/a.txt"))

        assertThat(server.authHeaders().last()).isNotNull().startsWith("Digest ")
        assertThat(server.authHeaders().last()!!.param("algorithm")).isEqualTo("SHA-256")
    }

    @Test
    fun `stale Digest nonce is renewed and the request retried`() = runTest {
        val server = FakeServer(Scheme.Digest)
        server.service().downloadFile(CloudItemId("/a.txt"))

        server.nonce = "second"
        server.service.downloadFile(CloudItemId("/a.txt"))

        val headers = server.authHeaders()
        assertThat(headers).hasSize(4)
        assertThat(headers[2]!!.param("nonce")).isEqualTo("first")
        assertThat(headers[3]!!.param("nonce")).isEqualTo("second")
        assertThat(headers[3]!!.param("nc")).isEqualTo("00000001")
    }

    @Test
    fun `wrong Basic credentials throw NotAuthenticatedException without looping`() = runTest {
        val server = FakeServer(Scheme.Basic, password = "other")

        assertFailure { server.service().downloadFile(CloudItemId("/a.txt")) }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(server.requests).hasSize(2)
    }

    @Test
    fun `wrong Digest credentials throw NotAuthenticatedException without looping`() = runTest {
        val server = FakeServer(Scheme.Digest, password = "other")

        assertFailure { server.service().downloadFile(CloudItemId("/a.txt")) }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(server.requests).hasSize(2)
    }

    @Test
    fun `401 without challenge falls back to Basic`() = runTest {
        val server = FakeServer(Scheme.Basic, sendChallenge = false)

        server.service().downloadFile(CloudItemId("/a.txt"))

        assertThat(server.authHeaders()).containsExactly(null, BASIC_HEADER)
    }

    @Test
    fun `server without authentication gets no credentials`() = runTest {
        val server = FakeServer(Scheme.None)

        server.service().downloadFile(CloudItemId("/a.txt"))
        server.service.createFile("/b.bin".asFilePath(), { bufferOf(3) }, 3, "image/png")
        server.service.createFile("/c.bin".asFilePath(), { bufferOf(3) }, 3, "image/png")

        assertThat(server.authHeaders().distinct()).containsExactly(null)
        // Only the first streaming upload probes the scheme
        assertThat(server.requests.map { it.method.value }).containsExactly(
            "GET", "PROPFIND", "PUT", "PROPFIND", "PUT", "PROPFIND"
        )
    }

    @Test
    fun `streaming upload to Digest server probes first and opens the source once`() = runTest {
        val server = FakeServer(Scheme.Digest)
        var opened = 0

        server.service().createFile(
            path = "/b.bin".asFilePath(),
            source = { opened++; bufferOf(3) },
            sizeInBytes = 3,
            mimeType = "image/png"
        )

        assertThat(opened).isEqualTo(1)
        assertThat(server.requests.map { it.method.value }).containsExactly(
            "PROPFIND", "PROPFIND", "PUT", "PROPFIND"
        )
        assertThat(server.authHeaders()[2]).isNotNull().startsWith("Digest ")
        assertThat(server.putBodies).containsExactly(listOf<Byte>(0, 1, 2))
    }

    @Test
    fun `streaming upload with stale nonce is resent with a new source`() = runTest {
        val server = FakeServer(Scheme.Digest)
        server.service().downloadFile(CloudItemId("/a.txt"))
        server.nonce = "second"
        var opened = 0

        server.service.createFile(
            path = "/b.bin".asFilePath(),
            source = { opened++; bufferOf(3) },
            sizeInBytes = null,
            mimeType = "image/png"
        )

        assertThat(opened).isEqualTo(2)
        // The 401'd first PUT, then the successful one. The replayed body never reached the server.
        assertThat(server.putBodies).containsExactly(listOf<Byte>(0, 1, 2), listOf<Byte>(0, 1, 2))
        assertThat(server.authHeaders().last()!!.param("nonce")).isEqualTo("second")
    }

    @Test
    fun `new credentials reset the detected scheme`() = runTest {
        val server = FakeServer(Scheme.Basic)
        server.service().downloadFile(CloudItemId("/a.txt"))

        server.service.setCredentials(
            WebDavCredentials("https://webdav.example.com/server.php", "user", "pass")
        )
        server.service.downloadFile(CloudItemId("/a.txt"))

        assertThat(server.authHeaders()).containsExactly(null, BASIC_HEADER, null, BASIC_HEADER)
    }
}

private enum class Scheme { None, Basic, Digest }

private const val BASIC_HEADER = "Basic dXNlcjpwYXNz" // user:pass

/**
 * A WebDAV server that checks the scheme, and for Digest the nonce and username. The Digest
 * response hash itself is covered by DigestChallengeTest.
 */
private class FakeServer(
    private val scheme: Scheme,
    private val password: String = "pass",
    private val alsoOfferBasic: Boolean = false,
    private val sendChallenge: Boolean = true
) {
    var nonce = "first"
    val requests = mutableListOf<HttpRequestData>()
    val putBodies = mutableListOf<List<Byte>>()
    lateinit var service: WebDavService

    private val engine = MockEngine { request ->
        requests += request
        if (request.method == HttpMethod.Put) {
            // Real engines always send the body, so read it to trigger writeTo()
            putBodies += request.body.toByteArray().toList()
        }
        if (!isAuthorized(request.headers[HttpHeaders.Authorization])) {
            return@MockEngine respondUnauthorized(request.headers[HttpHeaders.Authorization])
        }
        when (request.method) {
            HttpMethod.Get -> respond("content", HttpStatusCode.OK)
            HttpMethod.Put -> respond("", HttpStatusCode.Created)
            else -> respond(
                content = FILE_RESPONSE,
                status = HttpStatusCode.MultiStatus,
                headers = headersOf(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            )
        }
    }

    fun service(): WebDavService {
        service = WebDavService(
            credentials = WebDavCredentials(
                serverUrl = "https://webdav.example.com/server.php",
                username = "user",
                password = "pass"
            ),
            httpClient = HttpClient(engine) { applyDefaults() },
            rateLimiter = RateLimiter(10, Double.POSITIVE_INFINITY)
        )
        return service
    }

    fun authHeaders(): List<String?> = requests.map { it.headers[HttpHeaders.Authorization] }

    private fun isAuthorized(header: String?): Boolean {
        return when (scheme) {
            Scheme.None -> true
            Scheme.Basic -> password == "pass" && header == BASIC_HEADER
            Scheme.Digest -> password == "pass" && header != null &&
                header.startsWith("Digest ") &&
                header.param("username") == "user" &&
                header.param("nonce") == nonce
        }
    }

    private fun MockRequestHandleScope.respondUnauthorized(sentHeader: String?) = respond(
        content = "",
        status = HttpStatusCode.Unauthorized,
        headers = if (!sendChallenge) {
            Headers.Empty
        } else {
            Headers.build {
                when (scheme) {
                    Scheme.Basic -> append(HttpHeaders.WWWAuthenticate, "Basic realm=\"test\"")
                    else -> {
                        val stale = sentHeader?.startsWith("Digest ") == true &&
                            sentHeader.param("nonce") != nonce
                        append(
                            HttpHeaders.WWWAuthenticate,
                            "Digest realm=\"test\", qop=\"auth\", algorithm=SHA-256, " +
                                "nonce=\"$nonce\", opaque=\"xyz\"" + if (stale) ", stale=true" else ""
                        )
                        if (alsoOfferBasic) {
                            append(HttpHeaders.WWWAuthenticate, "Basic realm=\"test\"")
                        }
                    }
                }
            }
        }
    )
}

private fun String.param(name: String): String? {
    return Regex("""(?:^|[\s,])$name=("([^"]*)"|[^,\s]+)""").find(this)?.let {
        it.groupValues[2].ifEmpty { it.groupValues[1] }
    }
}

private const val FILE_RESPONSE =
    """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/server.php/b.bin</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>3</d:getcontentlength><d:getlastmodified>Tue, 02 Apr 2024 18:27:06 GMT</d:getlastmodified><d:displayname>b.bin</d:displayname><d:getetag>"abc123"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
