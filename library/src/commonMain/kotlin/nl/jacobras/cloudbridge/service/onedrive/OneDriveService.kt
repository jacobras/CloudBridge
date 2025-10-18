package nl.jacobras.cloudbridge.service.onedrive

import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
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
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil
import kotlin.time.Instant

public class OneDriveService(
    private val clientId: String
) : CloudService {

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

    public override fun getAuthenticator(redirectUri: String): PkceAuthenticator {
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

    override suspend fun getUserInfo(): UserInfo = tryCall {
        val response = api.getUserInfo()
        UserInfo(
            name = listOf(response.givenName, response.familyName).joinToString(separator = " "),
            emailAddress = response.email
        )
    }

    override suspend fun listFiles(path: FolderPath): List<CloudItem> = tryCall(path.toString()) {
        val response = if (path.isRoot) {
            api.listFiles()
        } else {
            api.listFiles(path.toString())
        }
        response.files.map {
            if (it.folder != null) {
                val folderPath = if (path.isRoot) {
                    it.name.asFolderPath()
                } else {
                    "${it.parent.path}/${it.name}".asFolderPath() // FIXME: parent includes internal Drive structure
                }
                CloudFolder(
                    id = Id(it.id),
                    path = folderPath,
                    name = it.name,
                )
            } else {
                val filePath = if (path.isRoot) {
                    it.name.asFilePath()
                } else {
                    "${it.parent.path}/${it.name}".asFilePath()
                }
                CloudFile(
                    id = Id(it.id),
                    path = filePath,
                    name = it.name,
                    sizeInBytes = it.size ?: error("Missing size for folder"),
                    modified = Instant.parse(it.lastModified)
                )
            }
        }
    }

    override suspend fun createFolder(path: FolderPath): Unit = tryCall {
        api.createFolder(
            json.encodeToString(
                CreateFolderArg(name = path.name)
            )
        )
    }

    override suspend fun createFile(path: FilePath, content: String): Unit = tryCall {
        api.createFile(
            path = path.toString(),
            content = content
        )
    }

    override suspend fun updateFile(id: Id, content: String): Unit = tryCall(id.value) {
        api.updateFile(
            id = id.value,
            content = content
        )
    }

    override suspend fun downloadFile(id: Id): String = tryCall(id.value) {
        api.downloadFileById(id.value)
    }

    override suspend fun delete(id: Id): Unit = tryCall(id.value) {
        api.deleteById(id.value)
    }

    private suspend fun <T> tryCall(itemId: String = "unknown", block: suspend () -> T): T {
        requireAuthHeader()

        try {
            return block()
        } catch (e: ResponseException) {
            throw when (e.response.status) {
                HttpStatusCode.NotFound -> {
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