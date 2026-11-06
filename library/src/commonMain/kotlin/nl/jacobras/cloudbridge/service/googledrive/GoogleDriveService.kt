package nl.jacobras.cloudbridge.service.googledrive

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.toByteArray
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.serialization.json.Json
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.TokenRefresher
import nl.jacobras.cloudbridge.auth.TokenResponse
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
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import nl.jacobras.cloudbridge.service.oauth.OAuthCloudService
import nl.jacobras.cloudbridge.util.forEachChunk
import nl.jacobras.cloudbridge.util.mapUnknownErrors
import nl.jacobras.cloudbridge.util.reportChunkSent
import nl.jacobras.cloudbridge.util.retryAfter
import nl.jacobras.cloudbridge.util.transferTo
import kotlin.time.Clock
import kotlin.time.Instant

public class GoogleDriveService internal constructor(
    clientInfo: ClientInfo,
    token: CloudAccessToken?,
    httpClientOverride: HttpClient? = null,
    rateLimiter: RateLimiter? = RateLimiter(
        maxConcurrent = 5,
        maxRatePerSecond = 5.0
    ),
    tokenRefresher: TokenRefresher? = googleDriveTokenRefresher(clientInfo),
    onTokenRefreshed: ((CloudAccessToken) -> Unit)? = null,
    clock: Clock = Clock.System
) : OAuthCloudService(
    clientInfo = clientInfo,
    startToken = token,
    httpClientOverride = httpClientOverride,
    rateLimiter = rateLimiter,
    tokenRefresher = tokenRefresher,
    onTokenRefreshed = onTokenRefreshed,
    clock = clock
), DeltaCloudService {

    override val baseUrl: String = "https://www.googleapis.com/"
    internal val api = ktorfit.createGoogleDriveApi()
    private val apiWithoutAuth = ktorfitWithoutAuth.createGoogleDriveApi()
    internal var uploadChunkSize = DEFAULT_CHUNK_SIZE
    private val appDataFolderId = tokenScopedCache<String>()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override val errorMapper: ResponseException.(String) -> CloudServiceException = { itemId ->
        when (response.status) {
            HttpStatusCode.NotFound -> {
                CloudServiceException.NotFoundException(itemId)
            }
            HttpStatusCode.Unauthorized -> {
                CloudServiceException.NotAuthenticatedException()
            }
            HttpStatusCode.TooManyRequests -> {
                CloudServiceException.RateLimitException(response.retryAfter())
            }
            HttpStatusCode.Forbidden
                if message?.contains("RateLimitExceeded", ignoreCase = true) == true -> {
                CloudServiceException.RateLimitException(response.retryAfter())
            }
            HttpStatusCode.Forbidden if message?.contains("storageQuotaExceeded") == true -> {
                CloudServiceException.InsufficientSpaceException()
            }
            else -> {
                CloudServiceException.Unknown(this)
            }
        }
    }

    override suspend fun requestTokenRefresh(
        refreshToken: String,
        clientId: String,
        clientSecret: String?
    ): TokenResponse = apiWithoutAuth.refreshToken(
        clientId = clientId,
        clientSecret = clientSecret,
        refreshToken = refreshToken
    )

    override suspend fun getUserInfo(): UserInfo = tryCall {
        val response = api.getUserInfo()
        UserInfo(
            name = response.user?.name,
            emailAddress = response.user?.emailAddress
        )
    }

    override suspend fun getFileInfo(path: FilePath): CloudFile = tryCall(path.toString()) {
        val folderPath = path.toFolderPath()
        val file = findByName(parent = folderPath.toParentId(), name = path.name)
            .firstOrNull { !it.isFolder }
            ?: throw CloudServiceException.NotFoundException(path.toString())
        file.toCloudFile(isRoot = folderPath.isRoot)
    }

    /**
     * The last part of a Drive folder path is the folder id, so the folder is fetched by id.
     */
    override suspend fun getFolderInfo(path: FolderPath): CloudFolder {
        require(!path.isRoot) { "Can't get info for the root folder" }

        val folder = tryCall(path.toString()) { api.getFile(path.name) }
        if (!folder.isFolder || folder.trashed) {
            throw CloudServiceException.NotFoundException(path.toString())
        }
        val isRoot = folder.isInRoot()
        return mapUnknownErrors {
            CloudFolder(
                id = CloudItemId(folder.id),
                path = folder.toFolderPath(isRoot = isRoot),
                name = folder.name
            )
        }
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
                    modified = Instant.parse(it.modified),
                    revision = it.version ?: error("Missing version for file")
                )
            }
        }
    }

    override suspend fun createFolder(path: FolderPath): CloudFolder {
        val parentId = path.parent.toParentId()
        ensureNameAvailable(parent = parentId, name = path.name)
        return tryCall(path.toString()) {
            val response = api.createFolder(
                json.encodeToString(
                    CreateFolderRequest(
                        name = path.name,
                        parents = listOf(parentId)
                    )
                )
            )
            CloudFolder(
                id = CloudItemId(response.id),
                path = response.toFolderPath(isRoot = path.parent.isRoot),
                name = response.name
            )
        }
    }

    override suspend fun createFile(path: FilePath, content: String): CloudFile {
        val metadata = path.toMetadata(mimeType = "text/plain")
        ensureNameAvailable(parent = metadata.parents.single(), name = metadata.name)
        return tryCall(path.toString()) {
            createSingle(
                metadata = metadata,
                content = content.toByteArray()
            ).toCloudFile(isRoot = path.toFolderPath().isRoot)
        }
    }

    override suspend fun updateFile(id: CloudItemId, content: String): CloudFile {
        val response = tryCall(id.value) {
            api.updateFile(
                id = id.value,
                contentType = "text/plain",
                content = content.toByteArray()
            )
        }
        return response.toCloudFile(isRoot = response.isInRoot())
    }

    override suspend fun createFile(
        path: FilePath,
        source: suspend () -> Source,
        sizeInBytes: Long?,
        mimeType: String,
        onProgress: ((bytesSent: Long, totalBytes: Long?) -> Unit)?
    ): CloudFile {
        val metadata = path.toMetadata(mimeType)
        ensureNameAvailable(parent = metadata.parents.single(), name = metadata.name)
        return upload(
            itemId = path.toString(),
            source = source,
            sizeInBytes = sizeInBytes,
            mimeType = mimeType,
            onProgress = onProgress,
            uploadSingle = { content -> createSingle(metadata, content) },
            startSession = {
                clientWithAuth.post("${baseUrl}upload/drive/v3/files") {
                    resumableUploadParams(mimeType, sizeInBytes)
                    setBody(
                        TextContent(
                            Json.encodeToString(metadata),
                            ContentType.Application.Json
                        )
                    )
                }
            }
        ).toCloudFile(isRoot = path.toFolderPath().isRoot)
    }

    override suspend fun updateFile(
        id: CloudItemId,
        source: suspend () -> Source,
        sizeInBytes: Long?,
        mimeType: String,
        onProgress: ((bytesSent: Long, totalBytes: Long?) -> Unit)?
    ): CloudFile {
        val response = upload(
            itemId = id.value,
            source = source,
            sizeInBytes = sizeInBytes,
            mimeType = mimeType,
            onProgress = onProgress,
            uploadSingle = { content -> api.updateFile(id.value, mimeType, content) },
            startSession = {
                clientWithAuth.patch("${baseUrl}upload/drive/v3/files/${id.value}") {
                    resumableUploadParams(mimeType, sizeInBytes)
                }
            }
        )
        return response.toCloudFile(isRoot = response.isInRoot())
    }

    override suspend fun moveFile(id: CloudItemId, newPath: FilePath): CloudFile {
        val response = move(id, newPath.toFolderPath(), newPath.name)
        return response.toCloudFile(isRoot = response.isInRoot())
    }

    override suspend fun moveFolder(id: CloudItemId, newPath: FolderPath): CloudFolder {
        require(!newPath.isRoot) { "Can't move a folder to the root folder itself" }

        val response = move(id, newPath.parent, newPath.name)
        val isRoot = response.isInRoot()
        return mapUnknownErrors {
            CloudFolder(
                id = CloudItemId(response.id),
                path = response.toFolderPath(isRoot = isRoot),
                name = response.name
            )
        }
    }

    /**
     * Renames the item and, if [parent] differs from its current parent, moves it there.
     */
    private suspend fun move(id: CloudItemId, parent: FolderPath, name: String): DriveFile {
        val currentParent = tryCall(id.value) { api.getFile(id.value) }.parents.firstOrNull()
        val newParent = parent.toParentId()
        ensureNameAvailable(parent = newParent, name = name, ownId = id.value)

        // "appDataFolder" is an alias, so resolve it to compare with the actual parent id
        val resolvedNewParent = if (parent.isRoot) getAppDataFolderId() else newParent
        val parentChanged = currentParent != resolvedNewParent
        return tryCall(id.value) {
            api.move(
                id = id.value,
                content = json.encodeToString(RenameRequest(name)),
                addParents = newParent.takeIf { parentChanged },
                removeParents = currentParent.takeIf { parentChanged }
            )
        }
    }

    /**
     * Google Drive allows multiple items with the same name in a folder, which is confusing.
     * To prevent that, this throws if [parent] already contains an item named [name] (other
     * than [ownId]). This is a best effort: a concurrent request can still create a duplicate.
     *
     * Must not be called from within [tryCall], as this fires off a request.
     *
     * @throws CloudServiceException.ConflictException
     */
    private suspend fun ensureNameAvailable(parent: String, name: String, ownId: String? = null) {
        if (tryCall(name) { findByName(parent, name) }.any { it.id != ownId }) {
            throw CloudServiceException.ConflictException(name)
        }
    }

    /**
     * Lists the (non-trashed) items named [name] in [parent].
     */
    private suspend fun findByName(parent: String, name: String): List<DriveFile> {
        val escapedName = name.replace("\\", "\\\\").replace("'", "\\'")
        return api.listFiles(
            query = "name = '$escapedName' and '$parent' in parents and trashed = false"
        ).files
    }

    /**
     * Whether this file is directly in the appDataFolder (of which the ID is fetched once per account
     * and cached). Must not be called from within [tryCall], as this fires off a request.
     */
    private suspend fun DriveFile.isInRoot(): Boolean {
        val rootId = getAppDataFolderId()
        return parents.firstOrNull()?.let { it == rootId } ?: true
    }

    /**
     * The app data folder ID, fetched once per account and then cached. Must not be called from
     * within [tryCall], as fetching it is a request of its own.
     */
    private suspend fun getAppDataFolderId(): String {
        return appDataFolderId.getOrFetch { tryCall { api.getAppDataFolder() }.id }
    }

    private fun FilePath.toMetadata(mimeType: String): DriveFileMetadata {
        return DriveFileMetadata(
            name = name,
            mimeType = mimeType,
            parents = listOf(toFolderPath().toParentId())
        )
    }

    /**
     * The id of this folder, to use as parent. The last part of a Drive folder path is the folder id.
     */
    private fun FolderPath.toParentId(): String {
        return if (isRoot) "appDataFolder" else name
    }

    private suspend fun createSingle(metadata: DriveFileMetadata, content: ByteArray): DriveFile {
        val boundary = "cloud-bridge-boundary"
        return api.createFile(
            map = MultiPartFormDataContent(
                boundary = boundary,
                contentType = ContentType.MultiPart.Related.withParameter("boundary", boundary),
                parts = formData {
                    append("metadata", Json.encodeToString(metadata), Headers.build {
                        append(HttpHeaders.ContentType, "application/json")
                    })
                    append("file", content, Headers.build {
                        append(HttpHeaders.ContentType, metadata.mimeType)
                    })
                }
            )
        )
    }

    private fun HttpRequestBuilder.resumableUploadParams(mimeType: String, sizeInBytes: Long?) {
        parameter("uploadType", "resumable")
        parameter("fields", FILE_FIELDS)
        header("X-Upload-Content-Type", mimeType)
        if (sizeInBytes != null) {
            header("X-Upload-Content-Length", sizeInBytes)
        }
    }

    /**
     * Uploads the content opened by [source] with [uploadSingle] if it fits in one chunk,
     * otherwise in a resumable upload session started by [startSession].
     *
     * Docs: https://developers.google.com/workspace/drive/api/guides/manage-uploads#resumable
     */
    private suspend fun upload(
        itemId: String,
        source: suspend () -> Source,
        sizeInBytes: Long?,
        mimeType: String,
        onProgress: ((bytesSent: Long, totalBytes: Long?) -> Unit)?,
        uploadSingle: suspend (ByteArray) -> DriveFile,
        startSession: suspend () -> HttpResponse
    ): DriveFile {
        var sessionUri: String? = null
        var response: DriveFile? = null
        forEachChunk(source, uploadChunkSize) { chunk, offset, isLast ->
            if (offset == 0L && isLast) {
                response = tryCall(itemId) { uploadSingle(chunk) }
                onProgress?.reportChunkSent(sizeInBytes, offset, chunk.size, isLast)
                return@forEachChunk
            }

            val uri = sessionUri ?: tryCall(itemId) {
                startSession().headers[HttpHeaders.Location] ?: error("Missing upload session URI")
            }.also { sessionUri = it }
            val end = offset + chunk.size - 1
            val total = if (isLast) (end + 1).toString() else sizeInBytes?.toString() ?: "*"

            // Do repeatable upload of chunk
            tryCall(itemId) {
                val chunkResponse = clientWithAuth.put(uri) {
                    expectSuccess = false
                    header(HttpHeaders.ContentRange, "bytes $offset-$end/$total")
                    setBody(ByteArrayContent(chunk, ContentType.parse(mimeType)))
                }

                // 308 "Resume Incomplete" means the chunk was received and more are expected
                val status = chunkResponse.status
                when {
                    status.value == HttpStatusCode.PermanentRedirect.value && !isLast -> Unit
                    status.isSuccess() && isLast -> {
                        response = json.decodeFromString<DriveFile>(chunkResponse.bodyAsText())
                    }
                    else -> throw ResponseException(chunkResponse, chunkResponse.bodyAsText())
                }
            }
            onProgress?.reportChunkSent(sizeInBytes, offset, chunk.size, isLast)
        }
        return checkNotNull(response)
    }

    private fun DriveFile.toCloudFile(isRoot: Boolean): CloudFile = mapUnknownErrors {
        CloudFile(
            id = CloudItemId(id),
            name = name,
            path = toFilePath(isRoot = isRoot),
            sizeInBytes = size?.toLongOrNull() ?: 0L,
            modified = Instant.parse(modified),
            revision = version ?: error("Missing version for file $name")
        )
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        api.downloadFile(id = id.value).decodeToString()
    }

    override suspend fun downloadFile(id: CloudItemId, sink: Sink): Unit = tryCall(id.value) {
        api.downloadFileStreaming(id = id.value).execute { it.bodyAsChannel().transferTo(sink) }
    }

    override suspend fun delete(id: CloudItemId): Unit = tryCall(id.value) {
        api.deleteById(id.value)
    }

    /**
     * Google Drive specifics: deleted items are only reported by id, so
     * [DeletedDeltaChange.path] is `/<fileId>`. See the library documentation on paths.
     */
    override suspend fun getDelta(previousCursor: DeltaCursor): DeltaResult {
        val rootId = getAppDataFolderId()

        if (previousCursor == DeltaCursor.INITIAL) {

            // Initial call does not return token that can be used for changes, so we'll fetch
            // it manually here. It's done before actual listing, because from this moment we
            // don't want to miss any changes.
            val startPageToken = tryCall { api.getStartPageToken() }.startPageToken

            val files = mutableListOf<DriveFile>()
            var pageToken: String? = null
            do {
                val currentPageToken = pageToken
                val response = tryCall { api.listFiles(query = null, pageToken = currentPageToken) }
                files += response.files
                pageToken = response.nextPageToken
            } while (pageToken != null)

            return mapUnknownErrors {
                DeltaResult(
                    newCursor = DeltaCursor(startPageToken),
                    changes = files.map { it.toUpdatedChange(rootId) }
                )
            }
        } else {
            val changes = mutableListOf<DriveChange>()
            var pageToken = previousCursor.value
            var newStartPageToken: String? = null
            while (newStartPageToken == null) {
                val currentPageToken = pageToken
                val response = tryCall { api.listChanges(pageToken = currentPageToken) }
                changes += response.changes
                newStartPageToken = response.newStartPageToken
                pageToken = mapUnknownErrors {
                    response.nextPageToken ?: newStartPageToken ?: error("Missing page token")
                }
            }

            return mapUnknownErrors {
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
}

private val DriveFile.isFolder: Boolean
    get() = mimeType == "application/vnd.google-apps.folder"

private const val DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024