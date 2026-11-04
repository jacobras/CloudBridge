package nl.jacobras.cloudbridge.service

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudBridge.defaultHttpClient
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import nl.jacobras.cloudbridge.util.TokenScopedCache

/**
 * @param httpClientOverride Client override for tests.
 * @param rateLimiter Limits the requests of this service, or `null` for no limits.
 */
public abstract class OAuthCloudService internal constructor(
    startToken: CloudAccessToken?,
    private val httpClientOverride: HttpClient?,
    rateLimiter: RateLimiter?
) : CloudService {

    private var token: CloudAccessToken? = startToken

    protected abstract val baseUrl: String

    protected val ktorfit: Ktorfit by lazy {
        ktorfit {
            baseUrl(baseUrl)
            httpClient(
                (httpClientOverride ?: defaultHttpClient).config {
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

    private val callDispatcher = CallDispatcher(
        rateLimiter = rateLimiter,
        authCheck = ::isAuthenticated,
        responseErrorMapper = { itemId -> errorMapper(itemId) }
    )

    protected abstract val errorMapper: ResponseException.(itemId: String) -> CloudServiceException

    /**
     * Authenticated client, for requests that are not convenient to use Ktorfit for.
     */
    internal val clientWithAuth: HttpClient
        get() = ktorfit.httpClient

    /**
     * Client without authentication, for pre-authenticated URLs.
     */
    internal val clientWithoutAuth: HttpClient
        get() = httpClientOverride ?: defaultHttpClient

    /**
     * Sets [token] to this service.
     *
     * @param token The access token to use for authentication, or `null` to authenticate a new
     * account with this service.
     */
    public fun setToken(token: CloudAccessToken?) {
        this.token = token
    }

    /**
     * Creates a cache of which the value is only reused as long as the token doesn't change.
     */
    internal fun <T : Any> tokenScopedCache() = TokenScopedCache<T> { token }

    override fun isAuthenticated(): Boolean {
        return token != null
    }

    private fun getAuthHeader(): String? {
        val token = token ?: return null
        return "Bearer ${token.accessToken}"
    }

    protected suspend fun <T> tryCall(itemId: String = "unknown", call: suspend () -> T): T {
        return callDispatcher.tryCall(itemId, call = call)
    }
}