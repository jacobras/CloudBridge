package nl.jacobras.cloudbridge.service.webdav

import io.ktor.utils.io.core.toByteArray
import kotlin.io.encoding.Base64

/**
 * @property serverUrl The server URL, e.g. `https://example.com/remote.php/dav/files/username`.
 */
public data class WebDavCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
) {
    override fun toString(): String = "serverUrl=$serverUrl, username=$username, password=***"

    internal fun toAuthHeaderValue(): String {
        return "Basic " + Base64.encode("$username:$password".toByteArray())
    }
}