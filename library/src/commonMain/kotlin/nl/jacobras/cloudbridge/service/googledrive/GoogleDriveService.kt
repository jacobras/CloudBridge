package nl.jacobras.cloudbridge.service.googledrive

import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.toByteArray
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.OAuthCloudService
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FilePath
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import kotlin.time.Instant

public class GoogleDriveService(
    token: CloudAccessToken? = null
) : OAuthCloudService(token) {

    override val baseUrl: String = "https://www.googleapis.com/"
    internal val api = ktorfit.createGoogleDriveApi()
    private val json = Json {
        encodeDefaults = true
    }

    override suspend fun getUserInfo(): UserInfo = tryCall {
        val response = api.getUserInfo()
        UserInfo(
            name = response.user?.name,
            emailAddress = response.user?.emailAddress
        )
    }

    override suspend fun listFiles(path: FolderPath): List<CloudItem> = tryCall(path.toString()) {
        val query = if (path.isRoot) {
            "'appDataFolder' in parents"
        } else {
            "'${path.name}' in parents"
        }
        api.listFiles(query = query).files.map {
            if (it.mimeType == "application/vnd.google-apps.folder") {
                val folderPath = if (path.isRoot) {
                    it.id.asFolderPath()
                } else {
                    "${it.parents.first()}/${it.id}".asFolderPath()
                }
                CloudFolder(
                    id = CloudItemId(it.id),
                    path = folderPath,
                    name = it.name
                )
            } else {
                val filePath = if (path.isRoot) {
                    it.name.asFilePath()
                } else {
                    "${it.parents.first()}/${it.name}".asFilePath()
                }
                CloudFile(
                    id = CloudItemId(it.id),
                    name = it.name,
                    path = filePath,
                    sizeInBytes = it.size?.toLongOrNull() ?: 0L,
                    modified = Instant.parse(it.modified)
                )
            }
        }
    }

    override suspend fun createFolder(path: FolderPath): Unit = tryCall {
        api.createFolder(
            json.encodeToString(
                CreateFolderRequest(
                    name = path.name,
                    parents = listOf("appDataFolder")
                )
            )
        )
    }

    override suspend fun createFile(path: FilePath, content: String): Unit = tryCall {
        val folderPath = path.toFolderPath()
        val parent = if (folderPath.isRoot) {
            "appDataFolder"
        } else {
            folderPath.name
        }
        val metadata = DriveFileMetadata(
            name = path.name,
            mimeType = "text/plain",
            parents = listOf(parent)
        )

        val boundary = "cloud-bridge-boundary"

        api.createFile(
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

    override suspend fun updateFile(id: CloudItemId, content: String): Unit = tryCall(id.value) {
        api.updateFile(
            id = id.value,
            content = content.toByteArray()
        )
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        api.downloadFile(id = id.value).decodeToString()
    }

    override suspend fun delete(id: CloudItemId): Unit = tryCall(id.value) {
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