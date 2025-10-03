package nl.jacobras.cloudbridge.providers.onedrive

import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudService

internal class OneDriveService(
    private val clientId: String,
    private val token: String
) : CloudService {

    private val ktorfit = ktorfit {
        baseUrl("https://graph.microsoft.com/")
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
    private val oneDriveApi = ktorfit.createOneDriveApi()

    internal suspend fun getToken(
        redirectUri: String,
        code: String,
        codeVerifier: String
    ): String {
        return oneDriveApi.getToken(
            clientId = clientId,
            redirectUri = redirectUri,
            code = code,
            codeVerifier = codeVerifier
        ).accessToken
    }

    override suspend fun listFiles(): List<String> {
        return oneDriveApi.listFiles(
            authorization = "Bearer $token"
        ).files.map { it.name }
    }
}