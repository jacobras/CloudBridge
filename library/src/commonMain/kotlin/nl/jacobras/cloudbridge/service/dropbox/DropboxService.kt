package nl.jacobras.cloudbridge.service.dropbox

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.toByteArray
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

/**
 * Instance of the Dropbox API.
 */
public class DropboxService : OAuthCloudService() {

    override val baseUrl: String = "https://api.dropboxapi.com/"
    internal val api = ktorfit.createDropboxApi()

    override suspend fun getUserInfo(): UserInfo = tryCall {
        val response = api.getUserInfo()
        UserInfo(
            name = response.name?.displayName,
            emailAddress = response.emailAddress
        )
    }

    override suspend fun listFiles(path: FolderPath): List<CloudItem> = tryCall(path.toString()) {
        val arg = Json.encodeToString(
            ListFolderArg(path = path.toString(withLeadingSlash = !path.isRoot))
        )
        api.listFiles(arg).entries.map {
            when (it.tag) {
                "file" -> {
                    CloudFile(
                        id = CloudItemId(it.id),
                        path = it.pathLower.asFilePath(),
                        name = it.name,
                        sizeInBytes = it.size ?: error("Missing size for file"),
                        modified = Instant.parse(
                            it.clientModified ?: error("Missing modified time for file")
                        )
                    )
                }
                "folder" -> {
                    CloudFolder(
                        id = CloudItemId(it.id),
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
            arguments = Json.encodeToString(
                DropboxUploadArg(
                    path = path.toString(),
                    mode = Mode.Add
                )
            ),
            content = content.toByteArray()
        )
    }

    override suspend fun updateFile(id: CloudItemId, content: String): Unit = tryCall(id.value) {
        api.uploadFile(
            arguments = Json.encodeToString(
                DropboxUploadArg(
                    path = id.value,
                    mode = Mode.Overwrite
                )
            ),
            content = content.toByteArray()
        )
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        api.downloadFile(
            arguments = Json.encodeToString(DropboxDownloadArg(path = id.value))
        )
    }

    override suspend fun delete(id: CloudItemId): Unit = tryCall(id.value) {
        api.deleteByPath(DeleteRequest(path = id.value))
    }

    private suspend fun <T> tryCall(itemId: String = "unknown", block: suspend () -> T): T {
        requireAuthenticated()

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