package nl.jacobras.cloudbridge.service.dropbox

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
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.PkceAuthenticator
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.FilePath
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.Id
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil
import kotlin.time.Instant

public class DropboxService(
    private val clientId: String
) : CloudService {

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

    public override fun getAuthenticator(redirectUri: String): PkceAuthenticator {
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

    override suspend fun listFiles(path: FolderPath): List<CloudItem> = tryCall(path.toString()) {
        val arg = Json.encodeToString(
            ListFolderArg(path = path.toString(withLeadingSlash = !path.isRoot))
        )
        api.listFiles(arg).entries.map {
            when (it.tag) {
                "file" -> {
                    CloudFile(
                        id = Id(it.id),
                        path = it.pathLower.asFilePath(),
                        name = it.name,
                        sizeInBytes = it.size ?: error("Missing size for file"),
                        modified = Instant.parse(it.clientModified ?: error("Missing modified time for file"))
                    )
                }
                "folder" -> {
                    CloudFolder(
                        id = Id(it.id),
                        path = it.pathLower.asFolderPath(),
                        name = it.name
                    )
                }
                else -> error("Unsupported tag: ${it.tag}")
            }
        }
    }

    override suspend fun createFolder(path: FolderPath): Unit = tryCall {
        api.createFolder(
            Json.encodeToString(
                CreateFolderRequest(path = "/" + path.name)
            )
        )
    }

    override suspend fun createFile(path: FilePath, content: String): Unit = tryCall {
        api.uploadFile(
            arguments = Json.encodeToString(DropboxUploadArg(path = path.toString())),
            content = content.toByteArray()
        )
    }

    override suspend fun downloadFile(id: Id): String = tryCall(id.value) {
        api.downloadFile(
            arguments = Json.encodeToString(DropboxDownloadArg(path = id.value))
        )
    }

    override suspend fun delete(id: Id): Unit = tryCall(id.value) {
        api.deleteByPath(DeleteRequest(path = id.value))
    }

    private suspend fun <T> tryCall(itemId: String = "unknown", block: suspend () -> T): T {
        requireAuthHeader()

        try {
            return block()
        } catch (e: ResponseException) {
            throw when (e.response.status) {
                HttpStatusCode.Conflict if e.message?.contains("not_found") == true -> {
                    CloudServiceException.NotFoundException(itemId)
                }
                HttpStatusCode.Unauthorized -> {
                    CloudServiceException.NotAuthenticatedException()
                }
                else -> {
                    CloudServiceException.Unknown(e)
                }
            }
        } catch (e: IOException) {
            throw CloudServiceException.ConnectionException(e)
        } catch (e: Throwable) {
            throw CloudServiceException.Unknown(e)
        }
    }
}