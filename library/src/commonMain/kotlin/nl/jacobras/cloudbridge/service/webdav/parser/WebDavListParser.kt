package nl.jacobras.cloudbridge.service.webdav.parser

import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.parse
import net.thauvin.erik.urlencoder.UrlEncoderUtil
import nl.jacobras.cloudbridge.service.webdav.xml.XmlNode
import nl.jacobras.cloudbridge.service.webdav.xml.XmlParser
import kotlin.time.Instant

internal object WebDavListParser {

    @Throws(WebDavParseException::class)
    fun parse(xml: String): List<WebDavResource> {
        val node = XmlParser.parse(xml)
        node.expectName("D:multistatus")

        return node.children
            .map { response ->
                response.expectName("D:response")

                val href = UrlEncoderUtil.decode(response.requireChild("D:href").value)

                val stats = response.children(PROP_STAT)
                val successFulStat = stats.first {
                    it.requireChild(STATUS).value == STATUS_OK
                }
                val props = successFulStat.requireChild(PROP)

                WebDavResource(
                    href = href,
                    isCollection = props
                        .requireChild(PROP_RESOURCE_TYPE)
                        .child(RESOURCE_TYPE_COLLECTION) != null,
                    displayName = props.requireChild(PROP_DISPLAY_NAME).value,
                    contentLength = null,
                    lastModified = Instant.parse(
                        input = props.requireChild(PROP_LAST_MODIFIED).value,
                        format = DateTimeComponents.Formats.RFC_1123
                    ),
                    etag = null
                )
            }
            .sortedBy { it.href }
    }

    @Throws(WebDavParseException::class)
    private fun XmlNode.expectName(name: String) {
        if (this.name != name.lowercase()) {
            throw WebDavParseException("Expected ${name.lowercase()} but found ${this.name}")
        }
    }

    private fun XmlNode.requireChild(name: String): XmlNode = child(name)
        ?: throw WebDavParseException("Required child $name not found")
}

private const val PROP = "D:prop"
private const val PROP_DISPLAY_NAME = "D:displayname"
private const val PROP_LAST_MODIFIED = "D:getlastmodified"
private const val PROP_RESOURCE_TYPE = "D:resourcetype"
private const val PROP_STAT = "D:propstat"
private const val RESOURCE_TYPE_COLLECTION = "D:collection"
private const val STATUS = "D:status"
private const val STATUS_OK = "HTTP/1.1 200 OK"