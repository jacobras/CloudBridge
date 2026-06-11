package nl.jacobras.cloudbridge.service.onedrive

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.OAuthCloudService
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

public class OneDriveService : OAuthCloudService() {

    override val baseUrl: String = "https://graph.microsoft.com/"
    internal val api = ktorfit.createOneDriveApi()
    private val json = Json {
        encodeDefaults = true
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
                    id = CloudItemId(it.id),
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
                    id = CloudItemId(it.id),
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

    override suspend fun updateFile(id: CloudItemId, content: String): Unit = tryCall(id.value) {
        api.updateFile(
            id = id.value,
            content = content
        )
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        api.downloadFileById(id.value)
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