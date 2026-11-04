package nl.jacobras.cloudbridge.service.webdav

/**
 * Credentials for a WebDAV server. Basic and Digest authentication are both supported; the
 * scheme is detected from the server's challenge.
 *
 * @property serverUrl The server URL, e.g. `https://example.com/remote.php/dav/files/username`.
 */
public data class WebDavCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
) {
    override fun toString(): String = "serverUrl=$serverUrl, username=$username, password=***"
}
