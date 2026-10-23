package nl.jacobras.cloudbridge

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudBridge.defaultHttpClient
import nl.jacobras.cloudbridge.auth.CloudAccessToken

/**
 * @param httpClient Client override for tests.
 */
public abstract class OAuthCloudService internal constructor(
    startToken: CloudAccessToken?,
    private val httpClient: HttpClient?
) : CloudService {
    public constructor(startToken: CloudAccessToken?) : this(startToken, null)

    private var token: CloudAccessToken? = startToken

    protected abstract val baseUrl: String

    protected val ktorfit: Ktorfit by lazy {
        ktorfit {
            baseUrl(baseUrl)
            httpClient(
                (httpClient ?: defaultHttpClient).config {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                isLenient = true
                                ignoreUnknownKeys = true
                            }
                        )
                    }
                    defaultRequest {
                        val authHeader = getAuthHeader()
                        if (authHeader != null) {
                            header(HttpHeaders.Authorization, authHeader)
                        }
                    }
                }
            )
        }
    }

    /**
     * Authenticated client, for requests that are not convenient to use Ktorfit for.
     */
    internal val clientWithAuth: HttpClient
        get() = ktorfit.httpClient

    /**
     * Client without authentication, for pre-authenticated URLs.
     */
    internal val clientWithoutAuth: HttpClient
        get() = httpClient ?: defaultHttpClient

    /**
     * Sets [token] to this service.
     *
     * @param token The access token to use for authentication, or `null` to authenticate a new
     * account with this service.
     */
    public fun setToken(token: CloudAccessToken?) {
        this.token = token
    }

    override fun isAuthenticated(): Boolean {
        return token != null
    }

    protected fun requireAuthenticated() {
        if (token == null) {
            throw CloudServiceException.NotAuthenticatedException()
        }
    }

    private fun getAuthHeader(): String? {
        val token = token ?: return null
        return "Bearer ${token.accessToken}"
    }
}