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
import nl.jacobras.cloudbridge.auth.CloudAuthenticator
import nl.jacobras.cloudbridge.auth.ImplicitAuthenticator
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
 * @param onSuccess A callback invoked when authentication succeeds.
 */
public class LocalAuthenticationServer(
    private val port: Int = 8080,
    private val onSuccess: (String) -> Unit
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
     * @see stop to stop the server.
     * @throws IllegalStateException If the server is already running.
     */
    public fun start(service: CloudService, clientId: String) {
        require(server == null) { "Server is already running; stop it first" }
        val redirectUri = "http://localhost:$port"
        val authenticator = service.getAuthenticator(clientId, redirectUri)
        val authUrl = authenticator.buildUri()

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
    }

    private suspend fun getToken(authenticator: CloudAuthenticator, url: String): String? {
        val params = parseQueryParams(url)

        return when (authenticator) {
            is ImplicitAuthenticator -> {
                params["access_token"]
            }
            is PkceAuthenticator -> {
                val code = params["code"] ?: return null
                authenticator.exchangeCodeForToken(code)
            }
        }
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