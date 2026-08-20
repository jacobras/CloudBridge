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
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
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
import nl.jacobras.cloudbridge.util.ensureSuffix

/**
 * Instance of a WebDAV server.
 */
public class WebDavService internal constructor(
    private var credentials: WebDavCredentials
) : CloudService {

    private val client = HttpClient {
        expectSuccess = true
        defaultRequest {
            header(HttpHeaders.Authorization, credentials.toAuthHeaderValue())
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
        val response = client.request(buildUrl(path.toString())) {
            method = HttpMethod("PROPFIND")
            setBody(PROPFIND_BODY)
        }
        val resources = withContext(Dispatchers.Default) {
            WebDavListParser.parse(response.bodyAsText())
        }
        resources
            .drop(1) // Skip the self-entity
            .map {
                if (it.isCollection) {
                    CloudFolder(
                        id = CloudItemId(it.href),
                        path = it.href.asFolderPath(),
                        name = it.displayName
                    )
                } else {
                    CloudFile(
                        id = CloudItemId(it.href),
                        path = it.href.asFilePath(),
                        name = it.displayName,
                        sizeInBytes = it.contentLength ?: -1L,
                        modified = it.lastModified
                    )
                }
            }
    }

    override suspend fun createFolder(path: FolderPath): Unit = tryCall(path.toString()) {
        client.request(buildUrl(path.toString(), suffixSlash = true)) {
            method = HttpMethod(("MKCOL"))
        }
    }

    override suspend fun createFile(path: FilePath, content: String): Unit =
        tryCall(path.toString()) {
            client.request(buildUrl(path.toString())) {
                method = HttpMethod.Put
                contentType(ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                setBody(content)
            }
        }

    override suspend fun updateFile(id: CloudItemId, content: String): Unit = tryCall(id.value) {
        client.request(buildUrl(id.value)) {
            method = HttpMethod.Put
            contentType(ContentType.Text.Plain.withCharset(Charsets.UTF_8))
            setBody(content)
        }
    }

    override suspend fun downloadFile(id: CloudItemId): String = tryCall(id.value) {
        client.request(buildUrl(id.value)) {
            method = HttpMethod.Get
        }.bodyAsText()
    }

    override suspend fun delete(id: CloudItemId): Unit = tryCall(id.value) {
        client.request(buildUrl(id.value)) {
            method = HttpMethod.Delete
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