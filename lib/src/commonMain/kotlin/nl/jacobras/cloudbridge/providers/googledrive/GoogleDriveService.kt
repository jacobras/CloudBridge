package nl.jacobras.cloudbridge.providers.googledrive

import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudAuthenticator
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil

public class GoogleDriveService(
    private val clientId: String
) : CloudService {

    private val token: String?
        get() = Settings.googleDriveToken

    private val ktorfit = ktorfit {
        baseUrl("https://www.googleapis.com/")
        httpClient(
            HttpClient {
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
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        )
    }
    private val api = ktorfit.createGoogleDriveApi()

    private fun requireAuthHeader(): String {
        val token = token ?: throw CloudServiceException.NotAuthenticatedException()
        return "Bearer $token"
    }

    override fun isAuthenticated(): Boolean {
        return token != null
    }

    public override fun getAuthenticator(redirectUri: String): CloudAuthenticator {
        val codeVerifier = Settings.codeVerifier ?: let {
            val verifier = SecurityUtil.createRandomCodeVerifier()
            Settings.codeVerifier = verifier
            verifier
        }
        return GoogleDriveAuthenticator(
            api = api,
            clientId = clientId,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )
    }

    override fun logout() {
        Settings.googleDriveToken = null
    }

    override suspend fun listFiles(): List<String> {
        requireAuthHeader()
        return api.listFiles().files.map { it.name }
    }
}