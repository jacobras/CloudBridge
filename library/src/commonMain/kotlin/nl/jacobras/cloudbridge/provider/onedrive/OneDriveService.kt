package nl.jacobras.cloudbridge.provider.onedrive

import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudAuthenticator
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.DirectoryPath
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil

public class OneDriveService(
    private val clientId: String
) : CloudService, CloudService.DownloadByPath {

    private val token: String?
        get() = Settings.oneDriveToken

    private val ktorfit = ktorfit {
        baseUrl("https://graph.microsoft.com/")
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
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        )
    }
    private val api = ktorfit.createOneDriveApi()
    private val json = Json {
        encodeDefaults = true
    }

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
        return OneDriveAuthenticator(
            api = api,
            clientId = clientId,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )
    }

    override fun logout() {
        Settings.oneDriveToken = null
    }

    override suspend fun listFiles(): List<CloudItem> = tryCall {
        api.listFiles().files.map {
            if (it.folder != null) {
                CloudFolder(
                    id = it.id,
                    name = it.name,
                )
            } else {
                CloudFile(
                    id = it.id,
                    name = it.name,
                    sizeInBytes = it.size ?: error("Missing size for folder")
                )
            }
        }
    }

    override suspend fun createFolder(path: DirectoryPath) {
        api.createFolder(
            json.encodeToString(
                CreateFolderArg(name = path.name)
            )
        )
    }

    override suspend fun createFile(filename: String, content: String): Unit = tryCall {
        api.uploadFile(
            path = filename,
            content = content
        )
    }

    override suspend fun downloadFileByPath(path: String): String = tryCall {
        api.downloadFile(path = path)
    }

    private suspend fun <T> tryCall(block: suspend () -> T): T {
        requireAuthHeader()

        try {
            return block()
        } catch (e: ResponseException) {
            throw if (e.response.status == HttpStatusCode.Unauthorized) {
                CloudServiceException.NotAuthenticatedException()
            } else {
                CloudServiceException.Unknown(e)
            }
        }
    }
}