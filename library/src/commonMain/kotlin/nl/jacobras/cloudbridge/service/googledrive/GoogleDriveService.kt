package nl.jacobras.cloudbridge.service.googledrive

import io.ktor.client.HttpClient
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

public class GoogleDriveService internal constructor(
    token: CloudAccessToken?,
    httpClient: HttpClient?
) : OAuthCloudService(token, httpClient), DeltaCloudService {

    public constructor(token: CloudAccessToken?) : this(token, null)

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
            if (it.isFolder) {
                CloudFolder(
                    id = CloudItemId(it.id),
                    path = it.toFolderPath(isRoot = path.isRoot),
                    name = it.name
                )
            } else {
                CloudFile(
                    id = CloudItemId(it.id),
                    name = it.name,
                    path = it.toFilePath(isRoot = path.isRoot),
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

    /**
     * Google Drive specifics: deleted items are only reported by id, so
     * [DeletedDeltaChange.path] is `/<fileId>`. See the library documentation on paths.
     */
    override suspend fun getDelta(previousCursor: DeltaCursor): DeltaResult = tryCall {
        val rootId = api.getAppDataFolder().id

        if (previousCursor == DeltaCursor.INITIAL) {

            // Initial call does not return token that can be used for changes, so we'll fetch
            // it manually here. It's done before actual listing, because from this moment we
            // don't want to miss any changes.
            val startPageToken = api.getStartPageToken().startPageToken

            val files = mutableListOf<DriveFile>()
            var pageToken: String? = null
            do {
                val response = api.listFiles(query = null, pageToken = pageToken)
                files += response.files
                pageToken = response.nextPageToken
            } while (pageToken != null)

            DeltaResult(
                newCursor = DeltaCursor(startPageToken),
                changes = files.map { it.toUpdatedChange(rootId) }
            )
        } else {
            val changes = mutableListOf<DriveChange>()
            var pageToken = previousCursor.value
            var newStartPageToken: String? = null
            while (newStartPageToken == null) {
                val response = api.listChanges(pageToken = pageToken)
                changes += response.changes
                newStartPageToken = response.newStartPageToken
                pageToken = response.nextPageToken ?: newStartPageToken ?: error("Missing page token")
            }

            DeltaResult(
                newCursor = DeltaCursor(newStartPageToken),
                changes = changes.map { change ->
                    val file = change.file
                    if (change.removed || file == null || file.trashed) {
                        DeletedDeltaChange(
                            itemId = CloudItemId(change.fileId),
                            path = "/${change.fileId}".asFilePath()
                        )
                    } else {
                        file.toUpdatedChange(rootId)
                    }
                }
            )
        }
    }

    private fun DriveFile.toUpdatedChange(rootId: String): UpdatedDeltaChange {
        val isRoot = parents.firstOrNull()?.let { it == rootId } ?: true
        return UpdatedDeltaChange(
            itemId = CloudItemId(id),
            path = if (isFolder) toFolderPath(isRoot) else toFilePath(isRoot),
            name = name,
            revision = version,
            modified = Instant.parse(modified)
        )
    }

    private fun DriveFile.toFolderPath(isRoot: Boolean): FolderPath {
        return if (isRoot) id.asFolderPath() else "${parents.first()}/$id".asFolderPath()
    }

    private fun DriveFile.toFilePath(isRoot: Boolean): FilePath {
        return if (isRoot) name.asFilePath() else "${parents.first()}/$name".asFilePath()
    }

    private suspend fun <T> tryCall(itemId: String = "unknown", block: suspend () -> T): T {
        requireAuthenticated()

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

private val DriveFile.isFolder: Boolean
    get() = mimeType == "application/vnd.google-apps.folder"