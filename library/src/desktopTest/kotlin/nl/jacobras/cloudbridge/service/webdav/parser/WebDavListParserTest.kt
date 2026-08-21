package nl.jacobras.cloudbridge.service.webdav.parser

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.time.Instant

class WebDavListParserTest {

    @Test
    fun test() {
        val xml = readFile("/webdav_propfind_response.xml")

        val res = WebDavListParser.parse(xml)
        assertThat(res).hasSize(5)

        // Skipping over the self-entity
        val res1 = res[1]
        assertThat(res1.href).isEqualTo("/AAA/")
        assertThat(res1.displayName).isEqualTo("AAA")
        assertThat(res1.lastModified).isEqualTo(Instant.parse("2026-08-20T21:55:51Z"))
        assertThat(res1.isCollection).isTrue()

        val res2 = res[2]
        assertThat(res2.href).isEqualTo("/BBB.txt")
        assertThat(res2.displayName).isEqualTo("BBB.txt")
        assertThat(res2.lastModified).isEqualTo(Instant.parse("2026-08-20T21:55:59Z"))
        assertThat(res2.isCollection).isFalse()

        val res3 = res[3]
        assertThat(res3.href).isEqualTo("/Hello world!.txt")
        assertThat(res3.displayName).isEqualTo("Hello world!.txt")
        assertThat(res3.lastModified).isEqualTo(Instant.parse("2026-08-20T20:36:34Z"))
        assertThat(res3.isCollection).isFalse()
    }

    private fun readFile(filename: String): String {
        return object {}.javaClass.getResource(filename)!!.readText()
    }
}