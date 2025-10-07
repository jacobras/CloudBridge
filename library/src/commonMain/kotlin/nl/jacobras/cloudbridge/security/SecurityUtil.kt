package nl.jacobras.cloudbridge.security

import io.ktor.utils.io.core.toByteArray
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.random.Random

internal object SecurityUtil {

    fun createRandomCodeVerifier(length: Int = 64): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return (1..length)
            .map { charset[Random.nextInt(charset.length)] }
            .joinToString("")
    }

    fun buildCodeChallenge(codeVerifier: String): String {
        val bytes = SHA256().digest(codeVerifier.toByteArray())
        return Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(bytes)
    }
}