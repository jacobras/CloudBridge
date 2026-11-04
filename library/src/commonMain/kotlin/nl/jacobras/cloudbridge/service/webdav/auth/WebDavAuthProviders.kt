package nl.jacobras.cloudbridge.service.webdav.auth

import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.util.generateNonce
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.jacobras.cloudbridge.service.webdav.WebDavCredentials
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64

/**
 * Digest authentication (RFC 7616) for the Ktor `Auth` plugin.
 *
 * Unlike Ktor's own digest provider, this one supports SHA-256 and SHA-512-256, and keeps
 * sending credentials preemptively once a nonce is known. That avoids a 401 round-trip per
 * request, and avoids resending one-shot streaming bodies.
 */
internal class WebDavDigestAuthProvider(
    private val credentials: () -> WebDavCredentials
) : AuthProvider {
    private val mutex = Mutex()

    @Volatile
    private var challenge: DigestChallenge? = null
    private var nonceCount = 0

    val hasChallenge: Boolean
        get() = challenge != null

    @Deprecated("Use sendWithoutRequest(request)")
    override val sendWithoutRequest: Boolean
        get() = error("Use sendWithoutRequest(request)")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = challenge != null

    override fun isApplicable(auth: HttpAuthHeader): Boolean = DigestChallenge.from(auth) != null

    /**
     * Called preemptively with [authHeader] `null`, or with the server's challenge when the Auth
     * plugin answers a 401 to a request that was sent without credentials.
     */
    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        val value = mutex.withLock {
            val newChallenge = authHeader?.let(DigestChallenge::from)
            if (newChallenge != null && newChallenge.nonce != challenge?.nonce) {
                challenge = newChallenge
                nonceCount = 0
            }
            val challenge = challenge ?: return
            nonceCount++
            val credentials = credentials()
            challenge.authorization(
                username = credentials.username,
                password = credentials.password,
                method = request.method.value,
                uri = request.url.build().encodedPathAndQuery,
                nonceCount = nonceCount,
                cnonce = generateNonce()
            )
        }
        request.headers[HttpHeaders.Authorization] = value
    }

    /**
     * Called on a 401 to a request that was sent with Digest. Returns whether a retry makes
     * sense: only when the nonce was stale. Otherwise the credentials are wrong.
     */
    override suspend fun refreshToken(response: HttpResponse): Boolean {
        val newChallenge = DigestChallenge.strongestOf(
            parseChallenges(response.headers.getAll(HttpHeaders.WWWAuthenticate))
        ) ?: return false
        if (!newChallenge.stale) return false

        mutex.withLock {
            challenge = newChallenge
            nonceCount = 0
        }
        return true
    }

    fun clear() {
        challenge = null
    }
}

/**
 * Basic authentication for the Ktor `Auth` plugin. Credentials are only sent after the server
 * asked for Basic, so they never go out in cleartext to a server that uses Digest.
 */
internal class WebDavBasicAuthProvider(
    private val credentials: () -> WebDavCredentials
) : AuthProvider {

    @Volatile
    var isDetected: Boolean = false
        private set

    @Deprecated("Use sendWithoutRequest(request)")
    override val sendWithoutRequest: Boolean
        get() = error("Use sendWithoutRequest(request)")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = isDetected

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        return auth.authScheme.equals(AuthScheme.Basic, ignoreCase = true)
    }

    /**
     * Called preemptively with [authHeader] `null`, or with the server's Basic challenge when
     * the Auth plugin answers a 401 to a request that was sent without credentials.
     */
    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        if (authHeader != null) {
            isDetected = true
        }
        val credentials = credentials()
        val token = Base64.encode("${credentials.username}:${credentials.password}".toByteArray())
        request.headers[HttpHeaders.Authorization] = "${AuthScheme.Basic} $token"
    }

    /**
     * Called on a 401 to a request that was sent with Basic, so the credentials are wrong.
     */
    override suspend fun refreshToken(response: HttpResponse): Boolean = false

    /**
     * Falls back to Basic for a 401 without a readable challenge, e.g. in browsers when the
     * server doesn't expose `WWW-Authenticate` through CORS. Returns whether it switched.
     */
    fun detectWithoutChallenge(): Boolean {
        if (isDetected) return false
        isDetected = true
        return true
    }

    fun clear() {
        isDetected = false
    }
}
