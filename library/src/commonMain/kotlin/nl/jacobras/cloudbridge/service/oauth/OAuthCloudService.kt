package nl.jacobras.cloudbridge.service.oauth

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
import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.TokenRefresher
import nl.jacobras.cloudbridge.auth.TokenResponse
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import nl.jacobras.cloudbridge.service.CallDispatcher
import nl.jacobras.cloudbridge.util.TokenScopedCache
import kotlin.time.Clock

/**
 * @param clientInfo Identifies the app with the cloud service.
 * @param httpClientOverride Client override for tests.
 * @param rateLimiter Limits the requests of this service, or `null` for no limits.
 * @param tokenRefresher Gets a new token when it expires.
 * @param onTokenRefreshed Callback to persist the new token after a refresh.
 * @param clock Clock to determine whether the token is about to expire.
 */
public abstract class OAuthCloudService internal constructor(
    internal val clientInfo: ClientInfo,
    startToken: CloudAccessToken?,
    private val httpClientOverride: HttpClient?,
    rateLimiter: RateLimiter?,
    tokenRefresher: TokenRefresher?,
    onTokenRefreshed: ((CloudAccessToken) -> Unit)?,
    clock: Clock
) : CloudService {

    private val tokenHolder = TokenHolder(
        startToken = startToken,
        tokenRefresher = tokenRefresher,
        onTokenRefreshed = onTokenRefreshed,
        endpoint = ::requestTokenRefresh,
        clock = clock
    )

    protected abstract val baseUrl: String

    protected val ktorfit: Ktorfit by lazy { buildKtorfit(withAuth = true) }

    /**
     * Ktorfit without authentication header, for refreshing the token.
     */
    internal val ktorfitWithoutAuth: Ktorfit by lazy { buildKtorfit(withAuth = false) }

    private fun buildKtorfit(withAuth: Boolean) = ktorfit {
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
                if (withAuth) {
                    defaultRequest {
                        val authHeader = getAuthHeader()
                        if (authHeader != null) {
                            header(HttpHeaders.Authorization, authHeader)
                        }
                    }
                }
            }
        )
    }

    /**
     * Gets a new access token using [refreshToken].
     */
    internal abstract suspend fun requestTokenRefresh(
        refreshToken: String,
        clientId: String,
        clientSecret: String?
    ): TokenResponse

    private val callDispatcher = CallDispatcher(
        rateLimiter = rateLimiter,
        authCheck = ::isAuthenticated,
        tokenHolder = tokenHolder,
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
        tokenHolder.token = token
    }

    /**
     * Creates a cache of which the value is only reused as long as the token doesn't change.
     */
    internal fun <T : Any> tokenScopedCache() = TokenScopedCache<T> { tokenHolder.token }

    override fun isAuthenticated(): Boolean {
        return tokenHolder.token != null
    }

    private fun getAuthHeader(): String? {
        val token = tokenHolder.token ?: return null
        return "Bearer ${token.accessToken}"
    }

    protected suspend fun <T> tryCall(itemId: String = "unknown", call: suspend () -> T): T {
        return callDispatcher.tryCall(itemId, call = call)
    }
}