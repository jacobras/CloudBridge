package nl.jacobras.cloudbridge.service.onedrive

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.decodeURLPart
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

public class OneDriveService internal constructor(
    token: CloudAccessToken?,
    httpClient: HttpClient?
) : OAuthCloudService(token, httpClient), DeltaCloudService {

    public constructor(token: CloudAccessToken?) : this(token, null)

    override val baseUrl: String = "https://graph.microsoft.com/"
    internal val api = ktorfit.createOneDriveApi()
    internal var uploadChunkSize = DEFAULT_CHUNK_SIZE
    private val appRoot = tokenScopedCache<DriveItemId>()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun getUserInfo(): UserInfo = tryCall {
        val response = api.getUserInfo()
        UserInfo(
            name = listOf(response.givenName, response.familyName).joinToString(separator = " "),
            emailAddress = response.email
        )
    }

    override suspend fun getFileInfo(path: FilePath): CloudFile = tryCall(path.toString()) {
        val item = api.getItemByPath(path.toString(withLeadingSlash = false))
        if (item.folder != null) {
            throw CloudServiceException.NotFoundException(path.toString())
        }
        item.toCloudFile(path)
    }

    override suspend fun getFolderInfo(path: FolderPath): CloudFolder {
        require(!path.isRoot) { "Can't get info for the root folder" }

        return tryCall(path.toString()) {
            val item = api.getItemByPath(path.toString(withLeadingSlash = false))
            if (item.folder == null) {
                throw CloudServiceException.NotFoundException(path.toString())
            }
            CloudFolder(id = CloudItemId(item.id), path = path, name = item.name)
        }
    }

    override suspend fun listFiles(path: FolderPath): List<CloudItem> = tryCall(path.toString()) {
        val response = if (path.isRoot) {
            api.listFiles()
        } else {
            api.listFiles(path.toString(withLeadingSlash = false))
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
                    modified = Instant.parse(it.lastModified),
                    revision = it.eTag ?: error("Missing eTag for file")
                )
            }
        }
    }

    override suspend fun createFolder(path: FolderPath): CloudFolder = tryCall {
        val content = json.encodeToString(CreateFolderArg(name = path.name))
        val response = if (path.parent.isRoot) {
            api.createFolder(content)
        } else {
            api.createFolder(path.parent.toString(withLeadingSlash = false), content)
        }
        CloudFolder(
            id = CloudItemId(response.id),
            path = path,
            name = response.name
        )
    }

    override suspend fun createFile(path: FilePath, content: String): CloudFile = tryCall {
        val response = api.createFile(
            path = path.toString(withLeadingSlash = false),
            contentType = "text/plain",
            content = content.encodeToByteArray()
        )
        response.toCloudFile(path)
    }

    override suspend fun updateFile(id: CloudItemId, content: String): CloudFile =
        tryCall(id.value) {
            val response = api.updateFile(
                id = id.value,
                contentType = "text/plain",
                content = content.encodeToByteArray()
            )
            response.toCloudFile(response.resolvePath())
        }

    override suspend fun createFile(
        path: FilePath,
        source: Source,
        sizeInBytes: Long?,
        mimeType: String
    ): CloudFile {
        return upload(
            itemId = path.toString(),
            source = source,
            sizeInBytes = sizeInBytes,
            uploadSingle = {
                api.createFile(
                    path.toString(withLeadingSlash = false),
                    mimeType,
                    it
                )
            },
            createSession = {
                api.createUploadSession(
                    path.toString(withLeadingSlash = false),
                    json.encodeToString(CreateUploadSessionArg())
                )
            }
        ).toCloudFile(path)
    }

    override suspend fun updateFile(
        id: CloudItemId,
        source: Source,
        sizeInBytes: Long?,
        mimeType: String
    ): CloudFile {
        val response = upload(
            itemId = id.value,
            source = source,
            sizeInBytes = sizeInBytes,
            uploadSingle = { api.updateFile(id.value, mimeType, it) },
            createSession = {
                api.createUploadSessionById(id.value, json.encodeToString(CreateUploadSessionArg()))
            }
        )
        return tryCall(id.value) { response.toCloudFile(response.resolvePath()) }
    }

    /**
     * Resolves the path of this item relative to the app root (of which the path is fetched once
     * per account and then cached).
     */
    private suspend fun DriveItem.resolvePath(): FilePath = resolveRelativePath().asFilePath()

    private suspend fun DriveItem.resolveRelativePath(): String {
        val root = getAppRootId()
        val parentPath = root.parent?.path ?: error("Missing parent path for app root")
        val rootName = root.name ?: error("Missing name for app root")
        val rootPath = "${parentPath.decodeURLPart()}/$rootName"

        // Graph returns e.g. "/drive/root:/Apps/My%20App/folder" for an item in "/folder".
        // Paths are percent-encoded, names aren't, so compare decoded.
        val relativeFolder = parent.path.decodeURLPart().removePrefix(rootPath)
        return "$relativeFolder/$name"
    }

    /**
     * The app root folder, fetched once per account and then cached.
     */
    private suspend fun getAppRootId(): DriveItemId {
        return appRoot.getOrFetch { api.getAppRoot() }
    }

    override suspend fun moveFile(id: CloudItemId, newPath: FilePath): CloudFile =
        tryCall(id.value) {
            val response = move(id, newPath.toFolderPath(), newPath.name)
            response.toCloudFile(response.resolvePath())
        }

    override suspend fun moveFolder(id: CloudItemId, newPath: FolderPath): CloudFolder {
        require(!newPath.isRoot) { "Can't move a folder to the root folder itself" }

        return tryCall(id.value) {
            val response = move(id, newPath.parent, newPath.name)
            CloudFolder(
                id = CloudItemId(response.id),
                path = response.resolveRelativePath().asFolderPath(),
                name = response.name
            )
        }
    }

    /**
     * Graph moves items by parent folder id, so that's looked up first.
     */
    private suspend fun move(id: CloudItemId, parent: FolderPath, name: String): DriveItem {
        val parentId = if (parent.isRoot) {
            getAppRootId().id
        } else {
            api.getItemByPath(parent.toString(withLeadingSlash = false)).id
        }
        val arg = MoveArg(
            name = name,
            parent = MoveParentReference(parentId)
        )
        return api.move(
            id = id.value,
            content = json.encodeToString(arg)
        )
    }

    /**
     * Uploads [source] with [uploadSingle] if it fits in one chunk, otherwise in an upload session
     * created by [createSession]. Upload sessions require [sizeInBytes].
     */
    @Suppress("UseRequire")
    private suspend fun upload(
        itemId: String,
        source: Source,
        sizeInBytes: Long?,
        uploadSingle: suspend (ByteArray) -> DriveItem,
        createSession: suspend () -> UploadSession
    ): DriveItem {
        return tryCall(itemId) {
            var uploadUrl: String? = null
            var response: DriveItem? = null
            source.forEachChunk(uploadChunkSize) { chunk, offset, isLast ->
                if (offset == 0L && isLast) {
                    response = uploadSingle(chunk)
                    return@forEachChunk
                }

                val total = sizeInBytes ?: throw CloudServiceException.IllegalArgumentException(
                    "sizeInBytes is required for OneDrive uploads larger than $uploadChunkSize bytes, but it was null"
                )
                val end = offset + chunk.size - 1
                if (!isLast && end >= total) {
                    throw CloudServiceException.IllegalArgumentException(
                        "Content is larger than sizeInBytes ($total bytes)"
                    )
                }
                if (isLast && end + 1 != total) {
                    throw CloudServiceException.IllegalArgumentException(
                        "Content size (${end + 1} bytes) doesn't match sizeInBytes ($total bytes)"
                    )
                }
                val url = uploadUrl ?: createSession().uploadUrl.also { uploadUrl = it }

                // The upload URL is pre-authenticated, so it needs to be sent without an Authorization header
                val chunkResponse = clientWithoutAuth.put(url) {
                    header(HttpHeaders.ContentRange, "bytes $offset-$end/$total")
                    setBody(ByteArrayContent(chunk, ContentType.Application.OctetStream))
                }
                if (isLast) {
                    response = json.decodeFromString<DriveItem>(chunkResponse.bodyAsText())
                }
            }
            checkNotNull(response)
        }
    }

    private fun DriveItem.toCloudFile(path: FilePath): CloudFile {
        return CloudFile(
            id = CloudItemId(id),
            path = path,
            name = name,
            sizeInBytes = size ?: error("Missing size for file"),
            modified = Instant.parse(lastModified),
            revision = eTag ?: error("Missing eTag for file")
        )
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        api.downloadFileById(id.value)
    }

    override suspend fun downloadFile(id: CloudItemId, sink: Sink): Unit = tryCall(id.value) {
        api.downloadFileByIdStreaming(id.value).execute { it.bodyAsChannel().transferTo(sink) }
    }

    override suspend fun delete(id: CloudItemId): Unit = tryCall(id.value) {
        api.deleteById(id.value)
    }

    override suspend fun getDelta(previousCursor: DeltaCursor): DeltaResult = tryCall {
        val rootId = api.getAppRoot().id

        var response = if (previousCursor == DeltaCursor.INITIAL) {
            api.getDelta()
        } else {
            api.getDeltaByUrl(previousCursor.value)
        }
        val items = response.items.toMutableList()
        while (response.deltaLink == null) {
            response = api.getDeltaByUrl(response.nextLink ?: error("Missing next link"))
            items += response.items
        }

        // The same item can appear more than once; the last occurrence is the latest.
        val itemsById = items.associateBy { it.id }
        val paths = mutableMapOf(rootId to "/")
        fun resolvePath(id: String): String? = paths.getOrPut(id) {
            val item = itemsById[id] ?: return null
            val name = item.name ?: return null
            val parentPath = resolvePath(item.parent?.id ?: return null) ?: return null
            if (parentPath == "/") "/$name" else "$parentPath/$name"
        }

        DeltaResult(
            newCursor = DeltaCursor(response.deltaLink),
            changes = itemsById.values.filter { it.id != rootId }.map {
                val path = resolvePath(it.id) ?: it.name?.let { name -> "/$name" } ?: "/${it.id}"
                val isFolder = it.folder != null
                if (it.deleted != null) {
                    DeletedDeltaChange(
                        itemId = CloudItemId(it.id),
                        path = if (isFolder) path.asFolderPath() else path.asFilePath()
                    )
                } else {
                    UpdatedDeltaChange(
                        itemId = CloudItemId(it.id),
                        path = if (isFolder) path.asFolderPath() else path.asFilePath(),
                        name = it.name ?: error("Missing name"),
                        revision = it.eTag,
                        modified = it.lastModified?.let(Instant::parse)
                    )
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
                HttpStatusCode.NotFound -> {
                    CloudServiceException.NotFoundException(itemId)
                }
                HttpStatusCode.Unauthorized -> {
                    CloudServiceException.NotAuthenticatedException()
                }
                HttpStatusCode.InsufficientStorage -> {
                    CloudServiceException.InsufficientSpaceException()
                }
                HttpStatusCode.Conflict -> {
                    CloudServiceException.ConflictException(itemId)
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

private const val DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024