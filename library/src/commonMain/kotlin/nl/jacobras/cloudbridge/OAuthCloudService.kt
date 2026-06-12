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
import nl.jacobras.cloudbridge.auth.CloudAccessToken

public abstract class OAuthCloudService(startToken: CloudAccessToken?) : CloudService {
    private var token: CloudAccessToken? = startToken

    protected abstract val baseUrl: String

    protected val ktorfit: Ktorfit by lazy {
        ktorfit {
            baseUrl(baseUrl)
            httpClient(
                HttpClient {
                    expectSuccess = true
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

    override fun setToken(token: CloudAccessToken?) {
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