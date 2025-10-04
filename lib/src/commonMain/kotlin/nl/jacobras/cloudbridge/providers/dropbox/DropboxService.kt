package nl.jacobras.cloudbridge.providers.dropbox

import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudService

internal class DropboxService(
    private val clientId: String,
    private val token: String
) : CloudService {

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
            }
        )
    }
    private val dropboxApi = ktorfit.createDropboxApi()

    internal suspend fun getToken(
        redirectUri: String,
        code: String,
        codeVerifier: String
    ): String {
        return dropboxApi.getToken(
            clientId = clientId,
            redirectUri = redirectUri,
            code = code,
            codeVerifier = codeVerifier
        ).accessToken
    }

    override suspend fun listFiles(): List<String> {
        return dropboxApi.listFiles(
            authorization = "Bearer $token"
        ).entries.map { it.name }
    }
}