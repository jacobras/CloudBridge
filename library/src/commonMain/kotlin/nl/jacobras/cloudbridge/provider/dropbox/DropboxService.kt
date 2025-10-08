package nl.jacobras.cloudbridge.provider.dropbox

import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.toByteArray
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

public class DropboxService(
    private val clientId: String
) : CloudService, CloudService.DownloadById {

    private val token: String?
        get() = Settings.dropboxToken

    private val ktorfit = ktorfit {
        baseUrl("https://api.dropboxapi.com/")
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

    override suspend fun listFiles(): List<CloudItem> = tryCall {
        api.listFiles().entries.map {
            when (it.tag) {
                "file" -> {
                    CloudFile(
                        id = it.id,
                        name = it.name,
                        sizeInBytes = it.size ?: error("Missing size for file")
                    )
                }
                "folder" -> {
                    CloudFolder(
                        id = it.id,
                        name = it.name
                    )
                }
                else -> error("Unsupported tag: ${it.tag}")
            }
        }
    }

    override suspend fun createFolder(path: DirectoryPath) {
        api.createFolder(
            Json.encodeToString(
                CreateFolderRequest(path = "/" + path.name)
            )
        )
    }

    override suspend fun createFile(filename: String, content: String): Unit = tryCall {
        api.uploadFile(
            arguments = Json.encodeToString(DropboxUploadArg(path = "/$filename")),
            content = content.toByteArray()
        )
    }

    override suspend fun downloadFileById(id: String): String = tryCall {
        api.downloadFile(
            arguments = Json.encodeToString(DropboxDownloadArg(path = id))
        )
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