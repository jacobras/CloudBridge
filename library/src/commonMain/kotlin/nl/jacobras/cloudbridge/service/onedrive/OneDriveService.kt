package nl.jacobras.cloudbridge.service.onedrive

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
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

public class OneDriveService internal constructor(
    token: CloudAccessToken?,
    httpClient: HttpClient?
) : OAuthCloudService(token, httpClient), DeltaCloudService {

    public constructor(token: CloudAccessToken?) : this(token, null)

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
            content = content
        )
        CloudFile(
            id = CloudItemId(response.id),
            path = path,
            name = response.name,
            sizeInBytes = response.size ?: error("Missing size for file"),
            modified = Instant.parse(response.lastModified),
            revision = response.eTag ?: error("Missing eTag for file")
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