package nl.jacobras.cloudbridge.service.dropbox

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.toByteArray
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.OAuthCloudService
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.delta.DeletedDeltaChange
import nl.jacobras.cloudbridge.delta.DeltaCloudService
import nl.jacobras.cloudbridge.delta.DeltaCursor
import nl.jacobras.cloudbridge.delta.DeltaResult
import nl.jacobras.cloudbridge.delta.UpdatedDeltaChange
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
public class DropboxService(token: CloudAccessToken?) : OAuthCloudService(token), DeltaCloudService {

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
                        id = CloudItemId(it.id ?: error("Missing id for file")),
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
                        id = CloudItemId(it.id ?: error("Missing id for folder")),
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

    /**
     * Dropbox specifics:
     * - Deleted items have no id, so [DeletedDeltaChange.itemId] is always `null`.
     * - Dropbox doesn't return whether a deleted item was a file or a folder, so
     *   [DeletedDeltaChange.path] is always a [FilePath]. A deleted folder implies
     *   all its children were deleted too.
     * - Folders have no revision or modified time, so those are `null`.
     */
    override suspend fun getDelta(previousCursor: DeltaCursor): DeltaResult = tryCall {
        var response = if (previousCursor == DeltaCursor.INITIAL) {
            api.listFiles(Json.encodeToString(ListFolderArg(path = "", recursive = true)))
        } else {
            api.listFilesContinue(Json.encodeToString(ListFolderContinueArg(previousCursor.value)))
        }
        val entries = response.entries.toMutableList()
        while (response.hasMore) {
            response = api.listFilesContinue(Json.encodeToString(ListFolderContinueArg(response.cursor)))
            entries += response.entries
        }

        DeltaResult(
            newCursor = DeltaCursor(response.cursor),
            changes = entries.map {
                when (it.tag) {
                    "file" -> {
                        UpdatedDeltaChange(
                            itemId = CloudItemId(it.id ?: error("Missing id for file")),
                            path = it.pathLower.asFilePath(),
                            name = it.name,
                            revision = it.rev,
                            modified = it.clientModified?.let(Instant::parse)
                        )
                    }
                    "folder" -> {
                        UpdatedDeltaChange(
                            itemId = CloudItemId(it.id ?: error("Missing id for folder")),
                            path = it.pathLower.asFolderPath(),
                            name = it.name,
                            revision = null,
                            modified = null
                        )
                    }
                    "deleted" -> {
                        DeletedDeltaChange(
                            itemId = null,
                            path = it.pathLower.asFilePath()
                        )
                    }
                    else -> error("Unsupported tag: ${it.tag}")
                }
            }
        )
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