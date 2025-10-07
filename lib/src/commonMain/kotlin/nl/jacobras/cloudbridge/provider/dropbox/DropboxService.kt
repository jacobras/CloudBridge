package nl.jacobras.cloudbridge.provider.dropbox

import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudAuthenticator
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil

public class DropboxService(
    private val clientId: String
) : CloudService {

    private val token: String?
        get() = Settings.dropboxToken

    private val ktorfit = ktorfit {
        baseUrl("https://api.dropboxapi.com/")
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
    private val api = ktorfit.createDropboxApi()

    private fun requireAuthHeader(): String {
        val token = token ?: throw CloudServiceException.NotAuthenticatedException()
        return "Bearer $token"
    }

    override fun isAuthenticated(): Boolean {
        return Settings.dropboxToken != null
    }

    public override fun getAuthenticator(redirectUri: String): CloudAuthenticator {
        val codeVerifier = Settings.codeVerifier ?: let {
            val verifier = SecurityUtil.createRandomCodeVerifier()
            Settings.codeVerifier = verifier
            verifier
        }
        return DropboxAuthenticator(
            api = api,
            clientId = clientId,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )
    }

    override fun logout() {
        Settings.dropboxToken = null
    }

    override suspend fun listFiles(): List<CloudFile> {
        requireAuthHeader()
        return api.listFiles().entries.map {
            CloudFile(
                id = it.id,
                name = it.name,
                sizeInBytes = it.size
            )
        }
    }

    override suspend fun createFile(filename: String, content: String) {
        requireAuthHeader()
        api.uploadFile(
            arguments = Json.encodeToString(DropboxUploadArg(path = "/$filename")),
            content = content.toByteArray()
        )
    }
}