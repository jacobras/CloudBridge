package nl.jacobras.cloudbridge.service.webdav

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.Sink
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
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import nl.jacobras.cloudbridge.service.CallDispatcher
import nl.jacobras.cloudbridge.service.webdav.auth.WebDavBasicAuthProvider
import nl.jacobras.cloudbridge.service.webdav.auth.WebDavDigestAuthProvider
import nl.jacobras.cloudbridge.service.webdav.parser.WebDavListParser
import nl.jacobras.cloudbridge.service.webdav.parser.WebDavResource
import nl.jacobras.cloudbridge.util.SafeSourceOpener
import nl.jacobras.cloudbridge.util.ensureSuffix
import nl.jacobras.cloudbridge.util.forEachChunk
import nl.jacobras.cloudbridge.util.retryAfter
import nl.jacobras.cloudbridge.util.transferTo

/**
 * Instance of a WebDAV server.
 */
public class WebDavService internal constructor(
    private var credentials: WebDavCredentials,
    private val httpClient: HttpClient? = null,
    rateLimiter: RateLimiter? = RateLimiter(
        maxConcurrent = 10,
        maxRatePerSecond = 10.0
    )
) : CloudService {

    private val digestAuth = WebDavDigestAuthProvider { credentials }
    private val basicAuth = WebDavBasicAuthProvider { credentials }

    /**
     * Whether a request without credentials succeeded, so the server needs no authentication.
     */
    private var isAuthNotRequired = false

    private val client by lazy {
        (httpClient ?: defaultHttpClient).config {
            // The scheme is detected from the server's 401 challenge. Digest goes first so it's
            // preferred when a server offers both.
            install(Auth) {
                providers += digestAuth
                providers += basicAuth
            }
        }.also { client ->
            client.plugin(HttpSend).intercept { request ->
                val call = execute(request)
                val isUnauthorized = call.response.status == HttpStatusCode.Unauthorized
                val hasChallenge = call.response.headers.contains(HttpHeaders.WWWAuthenticate)
                if (isUnauthorized && !hasChallenge && !digestAuth.hasChallenge &&
                    basicAuth.detectWithoutChallenge()
                ) {
                    basicAuth.addRequestHeaders(request, authHeader = null)
                    execute(request)
                } else {
                    call
                }
            }
        }
    }
    private val callDispatcher = CallDispatcher(
        rateLimiter = rateLimiter,
        authCheck = ::isAuthenticated,
        responseErrorMapper = { itemId ->
            when (response.status) {
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
                HttpStatusCode.TooManyRequests -> {
                    CloudServiceException.RateLimitException(response.retryAfter())
                }
                else -> {
                    CloudServiceException.Unknown(this)
                }
            }
        }
    )

    /**
     * Sets the [credentials] to authenticate with.
     */
    public fun setCredentials(credentials: WebDavCredentials) {
        this.credentials = credentials
        digestAuth.clear()
        basicAuth.clear()
        isAuthNotRequired = false
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

    override suspend fun getFileInfo(path: FilePath): CloudFile = tryCall(path.toString()) {
        propfind(path.toString(), depth = 0).single().toCloudItem() as? CloudFile
            ?: throw CloudServiceException.NotFoundException(path.toString())
    }

    override suspend fun getFolderInfo(path: FolderPath): CloudFolder {
        require(!path.isRoot) { "Can't get info for the root folder" }

        return tryCall(path.toString()) {
            propfind(path.toString(), depth = 0, suffixSlash = true).single()
                .toCloudItem() as? CloudFolder
                ?: throw CloudServiceException.NotFoundException(path.toString())
        }
    }

    override suspend fun listFiles(path: FolderPath): List<CloudItem> = tryCall {
        propfind(path.toString(), depth = 1)
            .drop(1) // Skip the self-entity
            .map { it.toCloudItem() }
    }

    override suspend fun createFolder(path: FolderPath): CloudFolder {
        tryCall(path.toString()) {
            client.request(buildUrl(path.toString(), suffixSlash = true)) {
                method = HttpMethod(("MKCOL"))
            }
        }

        return tryCall(path.toString()) {
            // MKCOL doesn't return any metadata, so fetch it separately
            propfind(path.toString(), depth = 0, suffixSlash = true)
                .single()
                .toCloudItem() as CloudFolder
        }
    }

    override suspend fun createFile(path: FilePath, content: String): CloudFile {
        val body = textContent(content)
        return upload(path.toString()) { put(path.toString(), body) }
    }

    override suspend fun createFile(
        path: FilePath,
        source: suspend () -> Source,
        sizeInBytes: Long?,
        mimeType: String,
        onProgress: ((bytesSent: Long, totalBytes: Long?) -> Unit)?
    ): CloudFile {
        return uploadSource(
            path = path.toString(),
            sizeInBytes = sizeInBytes,
            contentType = ContentType.parse(mimeType),
            source = source,
            onProgress = onProgress
        )
    }

    override suspend fun updateFile(id: CloudItemId, content: String): CloudFile {
        val body = textContent(content)
        return upload(id.value) { put(id.value, body) }
    }

    override suspend fun updateFile(
        id: CloudItemId,
        source: suspend () -> Source,
        sizeInBytes: Long?,
        mimeType: String,
        onProgress: ((bytesSent: Long, totalBytes: Long?) -> Unit)?
    ): CloudFile {
        return uploadSource(
            path = id.value,
            sizeInBytes = sizeInBytes,
            contentType = ContentType.parse(mimeType),
            source = source,
            onProgress = onProgress
        )
    }

    private suspend fun uploadSource(
        path: String,
        sizeInBytes: Long?,
        contentType: ContentType,
        source: suspend () -> Source,
        onProgress: ((bytesSent: Long, totalBytes: Long?) -> Unit)?
    ): CloudFile {
        ensureAuthSchemeKnown()

        val opener = SafeSourceOpener(source)
        return upload(path) {
            var isRetry = false
            while (true) {
                try {
                    val bytesSent = opener.open().use {
                        val content = SourceContent(it, sizeInBytes, contentType)
                        put(path, content)
                        content.bytesWritten
                    }
                    onProgress?.invoke(bytesSent, bytesSent)
                    break
                } catch (e: ReplayedBodyException) {
                    // A stale Digest nonce made the auth plugin resend the one-shot body.
                    // The nonce is renewed now, so send once more with a new source.
                    if (isRetry) throw CloudServiceException.NotAuthenticatedException()
                    isRetry = true
                }
            }
        }
    }

    /**
     * Streaming bodies can't be resent after a 401 challenge, so learn the auth scheme with
     * a cheap request before the first streaming upload.
     */
    private suspend fun ensureAuthSchemeKnown() {
        if (digestAuth.hasChallenge || basicAuth.isDetected || isAuthNotRequired) return

        tryCall {
            client.request(buildUrl("", suffixSlash = true)) {
                method = HttpMethod("PROPFIND")
                header("Depth", "0")
                contentType(ContentType.Application.Xml)
                setBody(PROPFIND_BODY)
            }
        }
        if (!digestAuth.hasChallenge && !basicAuth.isDetected) {
            isAuthNotRequired = true
        }
    }

    /**
     * Calls [put] and fetches the metadata of the uploaded file at [path].
     */
    private suspend fun upload(path: String, put: suspend () -> Unit): CloudFile {
        tryCall(path) { put() }

        return tryCall(path) {
            // PUT doesn't return any metadata, so fetch it separately
            propfind(path, depth = 0).single().toCloudItem() as CloudFile
        }
    }

    private suspend fun put(path: String, content: OutgoingContent) {
        val sourceContent = content as? SourceContent
        try {
            client.request(buildUrl(path)) {
                method = HttpMethod.Put
                setBody(content)
            }
        } catch (e: Exception) {
            if (sourceContent?.isReplayed == true) throw ReplayedBodyException()
            if (e is IOException) throw sourceContent?.failure ?: e
            throw e
        }
        if (sourceContent?.isReplayed == true) throw ReplayedBodyException()
        sourceContent?.failure?.let { throw it }
    }

    /**
     * Can be sent multiple times, as needed for retries after rate limiting.
     */
    private fun textContent(content: String): OutgoingContent {
        return ByteArrayContent(content.encodeToByteArray(), ContentType.parse(TEXT_MIME_TYPE))
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        client.request(buildUrl(id.value)) {
            method = HttpMethod.Get
        }.bodyAsText()
    }

    override suspend fun downloadFile(id: CloudItemId, sink: Sink): Unit = tryCall(id.value) {
        client.prepareGet(buildUrl(id.value)).execute { it.bodyAsChannel().transferTo(sink) }
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
        tryCall(id.value) {
            client.request(buildUrl(id.value, suffixSlash = isFolder)) {
                method = HttpMethod("MOVE")
                header("Destination", buildUrl(newPath, suffixSlash = isFolder))
                header("Overwrite", "F")
            }
        }

        return tryCall(newPath) {
            // MOVE doesn't return any metadata, so fetch it separately
            propfind(newPath, depth = 0, suffixSlash = isFolder).single().toCloudItem()
        }
    }

    override suspend fun delete(id: CloudItemId): Unit = tryCall(id.value) {
        client.request(buildUrl(id.value)) {
            method = HttpMethod.Delete
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

    private suspend fun <T> tryCall(
        itemId: String = "unknown",
        call: suspend () -> T
    ): T {
        return callDispatcher.tryCall(itemId, call)
    }
}

/**
 * Streams [source] as request body. WebDAV has no chunked upload, so large files are sent in
 * a single request, without holding the whole file in memory. As [source] can only be read
 * once, a retry needs a new instance with a new source.
 *
 * When reading fails or the source doesn't hold exactly [contentLength] bytes, the body is
 * cancelled so a truncated file is never stored, and the cause is kept in [failure].
 */
private class SourceContent(
    private val source: Source,
    override val contentLength: Long?,
    override val contentType: ContentType
) : OutgoingContent.WriteChannelContent() {
    var failure: CloudServiceException.SourceReadException? = null
        private set
    var bytesWritten = 0L
        private set

    /**
     * True when the body was requested a second time, which would send an empty or truncated
     * file. The request is cancelled instead.
     */
    var isReplayed = false
        private set
    private var isWritten = false

    override suspend fun writeTo(channel: ByteWriteChannel) {
        if (isWritten) {
            isReplayed = true
            channel.cancel(IOException("Streaming body can't be sent twice"))
            return
        }
        isWritten = true

        try {
            source.forEachChunk(STREAM_CHUNK_SIZE) { chunk, _, _ ->
                channel.writeFully(chunk)
                bytesWritten += chunk.size
            }
            if (contentLength != null && bytesWritten != contentLength) {
                throw CloudServiceException.SourceReadException(
                    IOException("Expected $contentLength bytes, but the source held $bytesWritten bytes")
                )
            }
        } catch (e: CloudServiceException.SourceReadException) {
            failure = e
            channel.cancel(e)
        }
    }
}

private class ReplayedBodyException : Exception()

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