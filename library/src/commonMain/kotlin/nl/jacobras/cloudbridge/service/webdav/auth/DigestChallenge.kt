package nl.jacobras.cloudbridge.service.webdav.auth

import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.utils.io.core.toByteArray
import org.kotlincrypto.hash.md.MD5
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA512_256

/**
 * Hash algorithms for Digest authentication (RFC 7616), strongest first.
 */
internal enum class DigestAlgorithm(
    val token: String,
    val isSession: Boolean,
    private val digest: (ByteArray) -> ByteArray
) {
    SHA_512_256("SHA-512-256", isSession = false, { SHA512_256().digest(it) }),
    SHA_512_256_SESS("SHA-512-256-sess", isSession = true, { SHA512_256().digest(it) }),
    SHA_256("SHA-256", isSession = false, { SHA256().digest(it) }),
    SHA_256_SESS("SHA-256-sess", isSession = true, { SHA256().digest(it) }),
    MD5("MD5", isSession = false, { MD5().digest(it) }),
    MD5_SESS("MD5-sess", isSession = true, { MD5().digest(it) });

    fun hash(value: String): String {
        return digest(value.toByteArray()).joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    companion object {
        fun fromToken(token: String): DigestAlgorithm? {
            return entries.firstOrNull { it.token.equals(token, ignoreCase = true) }
        }
    }
}

/**
 * A Digest challenge from a `WWW-Authenticate` header.
 *
 * @property useQop True when the server offers `qop=auth`, false for legacy RFC 2069
 * challenges without `qop`.
 */
internal data class DigestChallenge(
    val realm: String,
    val nonce: String,
    val opaque: String?,
    val algorithm: DigestAlgorithm,
    val useQop: Boolean,
    val stale: Boolean
) {

    /**
     * Builds the `Authorization` header value for a request (RFC 7616, section 3.4).
     *
     * @param uri The request target, exactly as sent: encoded path plus query.
     * @param nonceCount Number of requests sent with this [nonce], starting at 1.
     */
    fun authorization(
        username: String,
        password: String,
        method: String,
        uri: String,
        nonceCount: Int,
        cnonce: String
    ): String {
        val nc = nonceCount.toString(16).padStart(8, '0')
        var ha1 = algorithm.hash("$username:$realm:$password")
        if (algorithm.isSession) {
            ha1 = algorithm.hash("$ha1:$nonce:$cnonce")
        }
        val ha2 = algorithm.hash("$method:$uri")
        val response = if (useQop) {
            algorithm.hash("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            algorithm.hash("$ha1:$nonce:$ha2")
        }

        return buildList {
            add("username=${username.quoted()}")
            add("realm=${realm.quoted()}")
            add("nonce=${nonce.quoted()}")
            add("uri=${uri.quoted()}")
            add("algorithm=${algorithm.token}")
            if (useQop) {
                add("qop=auth")
                add("nc=$nc")
                add("cnonce=${cnonce.quoted()}")
            }
            add("response=${response.quoted()}")
            if (opaque != null) {
                add("opaque=${opaque.quoted()}")
            }
        }.joinToString(", ", prefix = "${AuthScheme.Digest} ")
    }

    companion object {

        /**
         * Returns the challenge in [header], or `null` if it isn't a Digest challenge that
         * can be answered (unknown algorithm, or only `qop=auth-int`, which needs the body).
         */
        fun from(header: HttpAuthHeader): DigestChallenge? {
            if (header !is HttpAuthHeader.Parameterized) return null
            if (!header.authScheme.equals(AuthScheme.Digest, ignoreCase = true)) return null

            val realm = header.parameter("realm") ?: return null
            val nonce = header.parameter("nonce") ?: return null
            val algorithm = DigestAlgorithm.fromToken(header.parameter("algorithm") ?: "MD5")
                ?: return null
            val qop = header.parameter("qop")?.split(',')?.map { it.trim().lowercase() }
            val useQop = when {
                qop == null -> false
                "auth" in qop -> true
                else -> return null
            }
            return DigestChallenge(
                realm = realm,
                nonce = nonce,
                opaque = header.parameter("opaque"),
                algorithm = algorithm,
                useQop = useQop,
                stale = header.parameter("stale").equals("true", ignoreCase = true)
            )
        }

        /**
         * Returns the strongest supported Digest challenge in [headers], or `null` if none.
         */
        fun strongestOf(headers: List<HttpAuthHeader>): DigestChallenge? {
            return headers.mapNotNull(::from).minByOrNull { it.algorithm.ordinal }
        }
    }
}

/**
 * Parses all challenges from the `WWW-Authenticate` headers of a response. A single header
 * may hold multiple challenges, e.g. `Digest realm="a", nonce="b", Basic realm="a"`.
 */
internal fun parseChallenges(headerValues: List<String>?): List<HttpAuthHeader> {
    return headerValues.orEmpty()
        .flatMap { it.split(CHALLENGE_SEPARATOR) }
        .mapNotNull { runCatching { parseAuthorizationHeader(it.trim()) }.getOrNull() }
}

/**
 * Matches the comma before a new challenge: an auth scheme followed by a space and a
 * parameter, as opposed to the comma between two parameters of the same challenge.
 */
private val CHALLENGE_SEPARATOR = Regex(""",\s*(?=[A-Za-z][\w!#$%&'*+.^`|~-]*\s+[\w!#$%&'*+.^`|~-]+=)""")

private fun String.quoted(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
