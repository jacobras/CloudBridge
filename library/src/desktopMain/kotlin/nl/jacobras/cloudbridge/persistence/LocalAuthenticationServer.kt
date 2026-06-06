package nl.jacobras.cloudbridge.persistence

import io.ktor.http.ContentType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.PkceAuthenticator
import nl.jacobras.cloudbridge.name
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * A simple HTTP server that can handle authentication redirects.
 *
 * The cloud service should be configured to allow redirects to `http://localhost:$port`.
 *
 * @param port The port to listen on. Defaults to 8080.
 */
public class LocalAuthenticationServer(
    private val port: Int = 8080
) {
    private var server: EmbeddedServer<*, *>? = null

    public val url: String
        get() = "http://localhost:$port"

    /**
     * Starts an embedded HTTP server on the specified [port] to handle authentication redirects.
     * It serves a simple HTML page with a button to start the authentication flow.
     *
     * This server listens for authentication codes in the redirect URL automatically and invokes
     * [onSuccess] with the code. This will also automatically [stop] the server.
     *
     * Can be called only once before calling [stop].
     *
     * @param service The cloud service to connect to.
     * @param authenticator The authenticator to use for authentication.
     * @param onSuccess A callback invoked when authentication succeeds.
     * @see stop to stop the server.
     * @throws IllegalStateException If the server is already running.
     */
    internal fun start(
        service: CloudService,
        authenticator: PkceAuthenticator,
        onSuccess: (CloudAccessToken) -> Unit
    ) {
        require(server == null) { "Server is already running; stop it first" }
        val authUrl = authenticator.buildPkceUri()

        server = embeddedServer(Netty, port = port) {
            routing {
                get("/") {
                    val token = getToken(authenticator, call.request.uri)
                    token?.let {
                        call.respondText("Success! You can close this window now.")
                        onSuccess(it)
                        stop()
                        return@get
                    }

                    val response = buildString {
                        append("<html>")
                        append("<p>Hello World!</p>")
                        append("<p><a href='$authUrl'>Login with ${service.name}</a></p>")
                        append("</html>")
                    }
                    call.respondText(
                        text = response,
                        contentType = ContentType.Text.Html
                    )
                }
            }
        }.start(wait = false)
    }

    /**
     * Stops the embedded HTTP server started by [start].
     *
     * Safe to call multiple times or without calling [start].
     */
    public fun stop() {
        server?.stop()
        server = null
    }

    private suspend fun getToken(authenticator: PkceAuthenticator, url: String): CloudAccessToken? {
        val params = parseQueryParams(url)
        val code = params["code"] ?: return null
        return authenticator.exchangeCodeForToken(code)
    }
}

private fun parseQueryParams(url: String): Map<String, String> {
    val uri = URI(url)
    val query = uri.rawQuery ?: return emptyMap()

    return query
        .split("&").associate {
            val (key, value) = it.split("=", limit = 2)
            URLDecoder.decode(key, StandardCharsets.UTF_8) to
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
}