package nl.jacobras.cloudbridge.service.webdav.xml

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test

class XmlParserTest {

    @Test
    fun `parse simple xml`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <message>
              <text>Hello, World!</text>
            </message>
        """.trimIndent()

        val res = XmlParser.parse(xml)

        assertThat(res.name).isEqualTo("message")
        assertThat(res.children).hasSize(1)
        assertThat(res.child("text")!!.value).isEqualTo("Hello, World!")
    }

    @Test
    fun `parse multistatus`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>http://www.example.com/Coll/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>Loop Demo</D:displayname>
                    <D:resource-id>
                      <D:href>urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf8</D:href>
                    </D:resource-id>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>http://www.example.com/Coll/Bar</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>Loop Demo</D:displayname>
                    <D:resource-id>
                      <D:href>urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf8</D:href>
                    </D:resource-id>
                  </D:prop>
                  <D:status>HTTP/1.1 208 Already Reported</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val res = XmlParser.parse(xml)

        assertThat(res.name).isEqualTo("d:multistatus")
        assertThat(res.value).isEmpty()
        assertThat(res.children).hasSize(2)

        val response1 = res.children.first()
        assertThat(response1.name).isEqualTo("d:response")
        assertThat(response1.value).isEmpty()
        assertThat(response1.children).hasSize(2)
        assertThat(response1.child("d:href")?.value).isEqualTo("http://www.example.com/Coll/")

        val propstat1 = response1.child("d:propstat")!!
        assertThat(propstat1.value).isEmpty()
        assertThat(propstat1.child("d:status")?.value).isEqualTo("HTTP/1.1 200 OK")
        assertThat(propstat1.child("d:prop")?.child("d:displayname")?.value).isEqualTo("Loop Demo")

        val response2 = res.children[1]
        assertThat(response2.child("d:href")?.value).isEqualTo("http://www.example.com/Coll/Bar")
        assertThat(response2.child("d:propstat")?.child("d:status")?.value)
            .isEqualTo("HTTP/1.1 208 Already Reported")
    }
}