package nl.jacobras.cloudbridge.service.onedrive

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.decodeURLPart
import kotlinx.io.IOException
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
import kotlin.time.Instant

public class OneDriveService internal constructor(
    token: CloudAccessToken?,
    httpClient: HttpClient?
) : OAuthCloudService(token, httpClient), DeltaCloudService {

    public constructor(token: CloudAccessToken?) : this(token, null)

    override val baseUrl: String = "https://graph.microsoft.com/"
    internal val api = ktorfit.createOneDriveApi()
    internal var uploadChunkSize = DEFAULT_CHUNK_SIZE
    private var appRootPath: String? = null
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
                    modified = Instant.parse(it.lastModified),
                    revision = it.eTag ?: error("Missing eTag for file")
                )
            }
        }
    }

    override suspend fun createFolder(path: FolderPath): CloudFolder = tryCall {
        val response = api.createFolder(
            json.encodeToString(
                CreateFolderArg(name = path.name)
            )
        )
        CloudFolder(
            id = CloudItemId(response.id),
            path = path,
            name = response.name
        )
    }

    override suspend fun createFile(path: FilePath, content: String): CloudFile = tryCall {
        val response = api.createFile(
            path = path.toString(),
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
            uploadSingle = { api.createFile(path.toString(), mimeType, it) },
            createSession = {
                api.createUploadSession(
                    path.toString(),
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
     * and then cached).
     */
    private suspend fun DriveItem.resolvePath(): FilePath {
        val rootPath = appRootPath ?: api.getAppRoot().let { root ->
            val parentPath = root.parent?.path ?: error("Missing parent path for app root")
            "${parentPath.decodeURLPart()}/${root.name ?: error("Missing name for app root")}"
        }.also { appRootPath = it }

        // Graph returns e.g. "/drive/root:/Apps/My%20App/folder" for an item in "/folder".
        // Paths are percent-encoded, names aren't, so compare decoded.
        val relativeFolder = parent.path.decodeURLPart().removePrefix(rootPath)
        return "$relativeFolder/$name".asFilePath()
    }

    /**
     * Uploads [source] with [uploadSingle] if it fits in one chunk, otherwise in an upload session
     * created by [createSession]. Upload sessions require [sizeInBytes].
     */
    private suspend fun upload(
        itemId: String,
        source: Source,
        sizeInBytes: Long?,
        uploadSingle: suspend (ByteArray) -> DriveItem,
        createSession: suspend () -> UploadSession
    ): DriveItem {
        var uploadUrl: String? = null
        var response: DriveItem? = null
        source.forEachChunk(uploadChunkSize) { chunk, offset, isLast ->
            if (offset == 0L && isLast) {
                response = tryCall(itemId) { uploadSingle(chunk) }
                return@forEachChunk
            }

            val total = requireNotNull(sizeInBytes) {
                "sizeInBytes is required for OneDrive uploads larger than $uploadChunkSize bytes"
            }
            val end = offset + chunk.size - 1
            check(if (isLast) end + 1 == total else end < total) {
                "Content size doesn't match sizeInBytes ($total)"
            }
            val url =
                uploadUrl ?: tryCall(itemId) { createSession() }.uploadUrl.also { uploadUrl = it }

            // The upload URL is pre-authenticated, so it needs to be sent without an Authorization header
            tryCall(itemId) {
                val chunkResponse = clientWithoutAuth.put(url) {
                    header(HttpHeaders.ContentRange, "bytes $offset-$end/$total")
                    setBody(ByteArrayContent(chunk, ContentType.Application.OctetStream))
                }
                if (isLast) {
                    response = json.decodeFromString<DriveItem>(chunkResponse.bodyAsText())
                }
            }
        }
        return checkNotNull(response)
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

private const val DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024