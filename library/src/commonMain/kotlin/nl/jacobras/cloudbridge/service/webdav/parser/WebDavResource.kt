package nl.jacobras.cloudbridge.service.webdav.parser

import kotlin.time.Instant

/**
 * A 207 Multi-Status response. A WebDAV call may return multiple of these.
 *
 * (https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/207)
 *
 * @property href Raw href.
 * @property isCollection Whether the resource is a collection (folder).
 * @property contentLength Content length in bytes.
 * @property lastModified Last modified timestamp.
 * @property etag Entity tag.
 */
internal data class WebDavResource(
    val href: String,
    val isCollection: Boolean,
    val displayName: String,
    val contentLength: Long?,
    val lastModified: Instant,
    val etag: String?
)