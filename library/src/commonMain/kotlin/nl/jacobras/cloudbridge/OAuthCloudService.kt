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
import nl.jacobras.cloudbridge.auth.AccessToken

public abstract class OAuthCloudService(
    private val token: AccessToken? = null
) : CloudService {

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
                        if (token != null) {
                            header(HttpHeaders.Authorization, requireAuthHeader())
                        }
                    }
                }
            )
        }
    }

    override fun isAuthenticated(): Boolean {
        return token != null
    }

    protected fun requireAuthHeader(): String {
        val token = token ?: throw CloudServiceException.NotAuthenticatedException()
        return "Bearer ${token.accessToken}"
    }
}