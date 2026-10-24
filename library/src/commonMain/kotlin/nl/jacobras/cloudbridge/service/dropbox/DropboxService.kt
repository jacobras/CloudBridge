package nl.jacobras.cloudbridge.service.dropbox

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.toByteArray
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source
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
import nl.jacobras.cloudbridge.util.forEachChunk
import nl.jacobras.cloudbridge.util.transferTo
import kotlin.time.Instant

/**
 * Instance of the Dropbox API.
 */
public class DropboxService internal constructor(
    token: CloudAccessToken?,
    httpClient: HttpClient?
) : OAuthCloudService(token, httpClient), DeltaCloudService {

    public constructor(token: CloudAccessToken?) : this(token, null)

    override val baseUrl: String = "https://api.dropboxapi.com/"
    internal val api = ktorfit.createDropboxApi()
    internal var uploadChunkSize = DEFAULT_CHUNK_SIZE

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
        api.listFiles(arg).entries.map { it.toCloudItem() }
    }

    override suspend fun getFileInfo(path: FilePath): CloudFile = tryCall(path.toString()) {
        api.getMetadata(Json.encodeToString(GetMetadataArg(path.toString()))).toCloudItem()
                as? CloudFile ?: throw CloudServiceException.NotFoundException(path.toString())
    }

    override suspend fun getFolderInfo(path: FolderPath): CloudFolder {
        require(!path.isRoot) { "Can't get info for the root folder" }

        return tryCall(path.toString()) {
            val arg = Json.encodeToString(GetMetadataArg(path.toString()))
            api.getMetadata(arg).toCloudItem() as? CloudFolder
                ?: throw CloudServiceException.NotFoundException(path.toString())
        }
    }

    override suspend fun moveFile(id: CloudItemId, newPath: FilePath): CloudFile {
        return move(id, newPath.toString()) as CloudFile
    }

    override suspend fun moveFolder(id: CloudItemId, newPath: FolderPath): CloudFolder {
        require(!newPath.isRoot) { "Can't move a folder to the root folder itself" }
        return move(id, newPath.toString()) as CloudFolder
    }

    private suspend fun move(id: CloudItemId, newPath: String): CloudItem = tryCall(id.value) {
        val response = api.move(
            Json.encodeToString(MoveArg(fromPath = id.value, toPath = newPath))
        )
        response.metadata.toCloudItem()
    }

    override suspend fun createFolder(path: FolderPath): CloudFolder = tryCall {
        val response = api.createFolder(
            Json.encodeToString(
                CreateFolderRequest(path = path.toString())
            )
        )
        CloudFolder(
            id = CloudItemId(response.metadata.id),
            path = response.metadata.pathDisplay.asFolderPath(),
            name = response.metadata.name
        )
    }

    override suspend fun createFile(path: FilePath, content: String): CloudFile = tryCall {
        val response = api.uploadFile(
            arguments = Json.encodeToString(
                DropboxUploadArg(
                    path = path.toString(),
                    mode = Mode.Add
                )
            ),
            content = content.toByteArray()
        )
        response.toCloudFile()
    }

    override suspend fun updateFile(id: CloudItemId, content: String): CloudFile =
        tryCall(id.value) {
            val response = api.uploadFile(
                arguments = Json.encodeToString(
                    DropboxUploadArg(
                        path = id.value,
                        mode = Mode.Overwrite
                    )
                ),
                content = content.toByteArray()
            )
            response.toCloudFile()
        }

    override suspend fun createFile(
        path: FilePath,
        source: Source,
        sizeInBytes: Long?,
        mimeType: String
    ): CloudFile {
        return upload(
            arg = DropboxUploadArg(path = path.toString(), mode = Mode.Add),
            source = source
        ).toCloudFile()
    }

    override suspend fun updateFile(
        id: CloudItemId,
        source: Source,
        sizeInBytes: Long?,
        mimeType: String
    ): CloudFile {
        return upload(
            arg = DropboxUploadArg(path = id.value, mode = Mode.Overwrite),
            source = source
        ).toCloudFile()
    }

    /**
     * Uploads [source] in a single request if it fits in one chunk, otherwise in an upload session.
     */
    private suspend fun upload(arg: DropboxUploadArg, source: Source): UploadResponse =
        tryCall(arg.path) {
            var sessionId: String? = null
            var response: UploadResponse? = null
            source.forEachChunk(uploadChunkSize) { chunk, offset, isLast ->
                when {
                    isLast && offset == 0L -> {
                        // Just one chunk: immediate upload.
                        response = api.uploadFile(
                            arguments = Json.encodeToString(arg),
                            content = chunk
                        )
                    }
                    offset == 0L -> {
                        // First chunk: start upload session.
                        sessionId = api.uploadSessionStart(
                            arguments = Json.encodeToString(UploadSessionStartArg()),
                            content = chunk
                        ).sessionId
                    }
                    isLast -> {
                        // Last chunk: finish upload session.
                        val cursor = UploadSessionCursor(
                            sessionId = checkNotNull(sessionId),
                            offset = offset
                        )
                        response = api.uploadSessionFinish(
                            arguments = Json.encodeToString(UploadSessionFinishArg(cursor, arg)),
                            content = chunk
                        )
                    }
                    else -> {
                        // Upload part within upload session...
                        val cursor = UploadSessionCursor(
                            sessionId = checkNotNull(sessionId),
                            offset = offset
                        )
                        api.uploadSessionAppend(
                            arguments = Json.encodeToString(UploadSessionAppendArg(cursor)),
                            content = chunk
                        )
                    }
                }
            }
            checkNotNull(response)
        }

    private fun UploadResponse.toCloudFile(): CloudFile {
        return CloudFile(
            id = CloudItemId(id),
            path = pathDisplay.asFilePath(),
            name = name,
            sizeInBytes = size,
            modified = Instant.parse(clientModified),
            revision = rev
        )
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        api.downloadFile(
            arguments = Json.encodeToString(DropboxDownloadArg(path = id.value))
        )
    }

    override suspend fun downloadFile(id: CloudItemId, sink: Sink): Unit = tryCall(id.value) {
        val arg = Json.encodeToString(DropboxDownloadArg(path = id.value))
        api.downloadFileStreaming(arg).execute { it.bodyAsChannel().transferTo(sink) }
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
            response = api.listFilesContinue(
                Json.encodeToString(ListFolderContinueArg(response.cursor))
            )
            entries += response.entries
        }

        DeltaResult(
            newCursor = DeltaCursor(response.cursor),
            changes = entries.map {
                when (it.tag) {
                    "file" -> {
                        UpdatedDeltaChange(
                            itemId = CloudItemId(it.id ?: error("Missing id for file")),
                            path = it.pathDisplay.asFilePath(),
                            name = it.name,
                            revision = it.rev,
                            modified = it.clientModified?.let(Instant::parse)
                        )
                    }
                    "folder" -> {
                        UpdatedDeltaChange(
                            itemId = CloudItemId(it.id ?: error("Missing id for folder")),
                            path = it.pathDisplay.asFolderPath(),
                            name = it.name,
                            revision = null,
                            modified = null
                        )
                    }
                    "deleted" -> {
                        DeletedDeltaChange(
                            itemId = null,
                            path = it.pathDisplay.asFilePath()
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
                HttpStatusCode.Conflict if e.message?.contains("insufficient_space") == true -> {
                    CloudServiceException.InsufficientSpaceException()
                }
                HttpStatusCode.Conflict if e.message?.contains("conflict") == true -> {
                    CloudServiceException.ConflictException(itemId)
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
        } catch (e: CloudServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CloudServiceException.Unknown(e)
        }
    }
}

private fun FileEntry.toCloudItem(): CloudItem {
    return when (tag) {
        "file" -> {
            CloudFile(
                id = CloudItemId(id ?: error("Missing id for file")),
                path = pathDisplay.asFilePath(),
                name = name,
                sizeInBytes = size ?: error("Missing size for file"),
                modified = Instant.parse(
                    clientModified ?: error("Missing modified time for file")
                ),
                revision = rev ?: error("Missing rev for file")
            )
        }
        "folder" -> {
            CloudFolder(
                id = CloudItemId(id ?: error("Missing id for folder")),
                path = pathDisplay.asFolderPath(),
                name = name
            )
        }
        else -> error("Unsupported tag: $tag")
    }
}

private const val DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024