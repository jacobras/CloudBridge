package nl.jacobras.cloudbridge.provider.googledrive

import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAuthenticator
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.DirectoryPath
import nl.jacobras.cloudbridge.persistence.Settings

public class GoogleDriveService(
    private val clientId: String
) : CloudService, CloudService.DownloadById {

    private val token: String?
        get() = Settings.googleDriveToken

    private val ktorfit = ktorfit {
        baseUrl("https://www.googleapis.com/")
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
    private val api = ktorfit.createGoogleDriveApi()
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
        return GoogleDriveAuthenticator(
            clientId = clientId,
            redirectUri = redirectUri
        )
    }

    override fun logout() {
        Settings.googleDriveToken = null
    }

    override suspend fun listFiles(): List<CloudItem> = tryCall {
        api.listFiles().files.map {
            if (it.mimeType == "application/vnd.google-apps.folder") {
                CloudFolder(
                    id = it.id,
                    name = it.name
                )
            } else {
                CloudFile(
                    id = it.id,
                    name = it.name,
                    sizeInBytes = it.size?.toLongOrNull() ?: 0L
                )
            }
        }
    }

    override suspend fun createFolder(path: DirectoryPath) {
        api.createFolder(
            json.encodeToString(
                CreateFolderRequest(
                    name = path.name,
                    parents = listOf("appDataFolder")
                )
            )
        )
    }

    override suspend fun createFile(filename: String, content: String): Unit = tryCall {
        val metadata = DriveFileMetadata(
            name = filename,
            mimeType = "text/plain",
            parents = listOf("appDataFolder")
        )

        val boundary = "cloud-bridge-boundary"

        api.uploadFile(
            map = MultiPartFormDataContent(
                boundary = boundary,
                contentType = ContentType.MultiPart.Related.withParameter("boundary", boundary),
                parts = formData {
                    append("metadata", Json.encodeToString(metadata), Headers.build {
                        append(HttpHeaders.ContentType, "application/json")
                    })
                    append("file", content, Headers.build {
                        append(HttpHeaders.ContentType, "text/plain")
                    })
                }
            )
        )
    }

    override suspend fun downloadFileById(id: String): String = tryCall {
        api.downloadFile(id = id).decodeToString()
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