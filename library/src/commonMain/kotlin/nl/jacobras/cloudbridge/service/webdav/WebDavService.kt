package nl.jacobras.cloudbridge.service.webdav

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Source
import nl.jacobras.cloudbridge.CloudBridge.defaultHttpClient
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FilePath
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import nl.jacobras.cloudbridge.service.webdav.parser.WebDavListParser
import nl.jacobras.cloudbridge.service.webdav.parser.WebDavResource
import nl.jacobras.cloudbridge.util.ensureSuffix
import nl.jacobras.cloudbridge.util.forEachChunk

/**
 * Instance of a WebDAV server.
 */
public class WebDavService internal constructor(
    private var credentials: WebDavCredentials,
    private val httpClient: HttpClient? = null
) : CloudService {

    private val client by lazy {
        (httpClient ?: defaultHttpClient).config {
            defaultRequest {
                header(HttpHeaders.Authorization, credentials.toAuthHeaderValue())
            }
        }
    }

    /**
     * Sets the [credentials] to authenticate with.
     */
    public fun setCredentials(credentials: WebDavCredentials) {
        this.credentials = credentials
    }

    override fun isAuthenticated(): Boolean {
        return true
    }

    override suspend fun getUserInfo(): UserInfo {
        return UserInfo(
            name = credentials.username,
            emailAddress = null
        )
    }

    override suspend fun listFiles(path: FolderPath): List<CloudItem> = tryCall {
        propfind(path.toString(), depth = 1)
            .drop(1) // Skip the self-entity
            .map { it.toCloudItem() }
    }

    override suspend fun createFolder(path: FolderPath): CloudFolder = tryCall(path.toString()) {
        client.request(buildUrl(path.toString(), suffixSlash = true)) {
            method = HttpMethod(("MKCOL"))
        }

        // MKCOL doesn't return any metadata, so fetch it separately
        propfind(path.toString(), depth = 0, suffixSlash = true).single()
            .toCloudItem() as CloudFolder
    }

    override suspend fun createFile(path: FilePath, content: String): CloudFile {
        val bytes = content.encodeToByteArray()
        return createFile(
            path = path,
            source = Buffer().apply { write(bytes) },
            sizeInBytes = bytes.size.toLong(),
            mimeType = TEXT_MIME_TYPE
        )
    }

    override suspend fun createFile(
        path: FilePath,
        source: Source,
        sizeInBytes: Long?,
        mimeType: String
    ): CloudFile = tryCall(path.toString()) {
        put(path.toString(), source, sizeInBytes, mimeType)

        // PUT doesn't return any metadata, so fetch it separately
        propfind(path.toString(), depth = 0).single().toCloudItem() as CloudFile
    }

    override suspend fun updateFile(id: CloudItemId, content: String): CloudFile {
        val bytes = content.encodeToByteArray()
        return updateFile(
            id = id,
            source = Buffer().apply { write(bytes) },
            sizeInBytes = bytes.size.toLong(),
            mimeType = TEXT_MIME_TYPE
        )
    }

    override suspend fun updateFile(
        id: CloudItemId,
        source: Source,
        sizeInBytes: Long?,
        mimeType: String
    ): CloudFile = tryCall(id.value) {
        put(id.value, source, sizeInBytes, mimeType)

        // PUT doesn't return any metadata, so fetch it separately
        propfind(id.value, depth = 0).single().toCloudItem() as CloudFile
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        client.request(buildUrl(id.value)) {
            method = HttpMethod.Get
        }.bodyAsText()
    }

    override suspend fun moveFile(id: CloudItemId, newPath: FilePath): CloudFile {
        return move(id, newPath.toString(), isFolder = false) as CloudFile
    }

    override suspend fun moveFolder(id: CloudItemId, newPath: FolderPath): CloudFolder {
        require(!newPath.isRoot) { "Can't move a folder to the root folder itself" }
        return move(id, newPath.toString(), isFolder = true) as CloudFolder
    }

    /**
     * Moves [id] to [newPath]. `Overwrite: F` makes the server respond with 412 Precondition
     * Failed if the target already exists.
     */
    private suspend fun move(id: CloudItemId, newPath: String, isFolder: Boolean): CloudItem {
        return tryCall(id.value) {
            client.request(buildUrl(id.value, suffixSlash = isFolder)) {
                method = HttpMethod("MOVE")
                header("Destination", buildUrl(newPath, suffixSlash = isFolder))
                header("Overwrite", "F")
            }

            // MOVE doesn't return any metadata, so fetch it separately
            propfind(newPath, depth = 0, suffixSlash = isFolder).single().toCloudItem()
        }
    }

    override suspend fun delete(id: CloudItemId): Unit = tryCall(id.value) {
        client.request(buildUrl(id.value)) {
            method = HttpMethod.Delete
        }
    }

    private suspend fun put(path: String, source: Source, sizeInBytes: Long?, mimeType: String) {
        client.request(buildUrl(path)) {
            method = HttpMethod.Put
            setBody(SourceContent(source, sizeInBytes, ContentType.parse(mimeType)))
        }
    }

    /**
     * Fetches the properties of [path] and, for [depth] 1, its direct children.
     *
     * @param suffixSlash True to suffix a slash, required for collections.
     */
    private suspend fun propfind(
        path: String,
        depth: Int,
        suffixSlash: Boolean = false
    ): List<WebDavResource> {
        val response = client.request(buildUrl(path, suffixSlash)) {
            method = HttpMethod("PROPFIND")
            header("Depth", depth.toString())
            contentType(ContentType.Application.Xml)
            setBody(PROPFIND_BODY)
        }
        return withContext(Dispatchers.Default) {
            WebDavListParser.parse(response.bodyAsText())
        }
    }

    private fun WebDavResource.toCloudItem(): CloudItem {
        // Hrefs include the server's own path, which buildUrl() adds again
        val serverPath = Url(credentials.serverUrl).encodedPath.decodeURLPart().trimEnd('/')
        val relativePath = href.removePrefix(serverPath)
        return if (isCollection) {
            CloudFolder(
                id = CloudItemId(relativePath),
                path = relativePath.asFolderPath(),
                name = displayName
            )
        } else {
            CloudFile(
                id = CloudItemId(relativePath),
                path = relativePath.asFilePath(),
                name = displayName,
                sizeInBytes = contentLength ?: -1L,
                modified = lastModified,
                revision = etag ?: error("Missing etag for file")
            )
        }
    }

    /**
     * Builds a URL-encoded request URL for [path].
     *
     * @param suffixSlash True to suffix a slash, required for collections.
     */
    private fun buildUrl(path: String, suffixSlash: Boolean = false): String {
        val builder = URLBuilder(credentials.serverUrl.trimEnd('/'))
        builder.appendPathSegments(path.split('/').filter { it.isNotEmpty() })
        val url = builder.buildString()
        return if (suffixSlash) {
            url.ensureSuffix("/")
        } else {
            url
        }
    }

    private suspend fun <T> tryCall(itemId: String = "unknown", block: suspend () -> T): T {
        try {
            return block()
        } catch (e: ResponseException) {
            throw when (e.response.status) {
                HttpStatusCode.Unauthorized -> {
                    CloudServiceException.NotAuthenticatedException()
                }

                HttpStatusCode.NotFound -> {
                    CloudServiceException.NotFoundException(itemId)
                }

                HttpStatusCode.InsufficientStorage -> {
                    CloudServiceException.InsufficientSpaceException()
                }

                HttpStatusCode.PreconditionFailed -> {
                    CloudServiceException.ConflictException(itemId)
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

/**
 * Streams [source] as request body. WebDAV has no chunked upload, so large files are sent in
 * a single request, without holding the whole file in memory.
 */
private class SourceContent(
    private val source: Source,
    override val contentLength: Long?,
    override val contentType: ContentType
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        source.forEachChunk(STREAM_CHUNK_SIZE) { chunk, _, _ ->
            channel.writeFully(chunk)
        }
    }
}

private const val TEXT_MIME_TYPE = "text/plain; charset=UTF-8"
private const val STREAM_CHUNK_SIZE = 64 * 1024
private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:displayname/>
    <d:getetag/>
  </d:prop>
</d:propfind>"""