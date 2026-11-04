package nl.jacobras.cloudbridge.service.webdav.auth

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import kotlin.test.Test

class DigestChallengeTest {

    // RFC 7616, section 3.9.1
    private val rfcChallenge = "Digest realm=\"http-auth@example.org\", qop=\"auth, auth-int\", " +
        "nonce=\"7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v\", " +
        "opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\""

    @Test
    fun `MD5 response matches RFC 7616 example`() {
        val header = rfcAuthorization("$rfcChallenge, algorithm=MD5")

        assertThat(header).contains("response=\"8ca523f5e9506fed4657c9700eebdbec\"")
    }

    @Test
    fun `SHA-256 response matches RFC 7616 example`() {
        val header = rfcAuthorization("$rfcChallenge, algorithm=SHA-256")

        assertThat(header).contains(
            "response=\"753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1\""
        )
    }

    @Test
    fun `authorization contains all fields`() {
        val header = rfcAuthorization("$rfcChallenge, algorithm=SHA-256")

        assertThat(header).isEqualTo(
            "Digest username=\"Mufasa\", realm=\"http-auth@example.org\", " +
                "nonce=\"7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v\", uri=\"/dir/index.html\", " +
                "algorithm=SHA-256, qop=auth, nc=00000001, " +
                "cnonce=\"f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ\", " +
                "response=\"753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1\", " +
                "opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\""
        )
    }

    @Test
    fun `missing algorithm defaults to MD5`() {
        val challenge = parse(rfcChallenge)

        assertThat(challenge).isNotNull().prop(DigestChallenge::algorithm).isEqualTo(DigestAlgorithm.MD5)
    }

    @Test
    fun `RFC 2069 challenge without qop is answered without nc and cnonce`() {
        // RFC 2617, section 3.5, without qop
        val challenge = parse("Digest realm=\"testrealm@host.com\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\"")!!

        val header = challenge.authorization(
            username = "Mufasa",
            password = "Circle Of Life",
            method = "GET",
            uri = "/dir/index.html",
            nonceCount = 1,
            cnonce = "0a4f113b"
        )

        val md5 = DigestAlgorithm.MD5
        val ha1 = md5.hash("Mufasa:testrealm@host.com:Circle Of Life")
        val ha2 = md5.hash("GET:/dir/index.html")
        val expected = md5.hash("$ha1:dcd98b7102dd2f0e8b11d0f600bfb0c093:$ha2")
        assertThat(challenge.useQop).isFalse()
        assertThat(header).doesNotContain("nc=")
        assertThat(header).doesNotContain("cnonce=")
        assertThat(header).contains("response=\"$expected\"")
    }

    @Test
    fun `session algorithm hashes nonce and cnonce into HA1`() {
        val header = rfcAuthorization("$rfcChallenge, algorithm=MD5-sess")

        val md5 = DigestAlgorithm.MD5
        val nonce = "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v"
        val cnonce = "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ"
        val ha1 = md5.hash("${md5.hash("Mufasa:http-auth@example.org:Circle of Life")}:$nonce:$cnonce")
        val ha2 = md5.hash("GET:/dir/index.html")
        val expected = md5.hash("$ha1:$nonce:00000001:$cnonce:auth:$ha2")
        assertThat(header).contains("algorithm=MD5-sess")
        assertThat(header).contains("response=\"$expected\"")
    }

    @Test
    fun `SHA-512-256 is supported`() {
        val header = rfcAuthorization("$rfcChallenge, algorithm=SHA-512-256")

        // SHA-512/256 hashes are 64 hex characters, like SHA-256
        assertThat(header.substringAfter("response=\"").substringBefore('"').length).isEqualTo(64)
    }

    @Test
    fun `auth-int only challenge is not supported`() {
        assertThat(parse("Digest realm=\"r\", nonce=\"n\", qop=\"auth-int\"")).isNull()
    }

    @Test
    fun `unknown algorithm is not supported`() {
        assertThat(parse("Digest realm=\"r\", nonce=\"n\", algorithm=SHA-1")).isNull()
    }

    @Test
    fun `strongest of multiple challenges is picked`() {
        val challenges = parseChallenges(
            listOf(
                "Digest realm=\"r\", nonce=\"a\", algorithm=MD5, qop=\"auth\"",
                "Digest realm=\"r\", nonce=\"b\", algorithm=SHA-256, qop=\"auth\", Basic realm=\"r\""
            )
        )

        assertThat(challenges.size).isEqualTo(3)
        assertThat(DigestChallenge.strongestOf(challenges)).isNotNull().given {
            assertThat(it.algorithm).isEqualTo(DigestAlgorithm.SHA_256)
            assertThat(it.nonce).isEqualTo("b")
        }
    }

    @Test
    fun `stale flag is parsed`() {
        assertThat(parse("Digest realm=\"r\", nonce=\"n\", stale=TRUE")!!.stale).isTrue()
        assertThat(parse("Digest realm=\"r\", nonce=\"n\"")!!.stale).isFalse()
    }

    @Test
    fun `quotes in values are escaped`() {
        val header = parse("Digest realm=\"r\", nonce=\"n\", qop=\"auth\"")!!.authorization(
            username = "a\"b",
            password = "p",
            method = "GET",
            uri = "/",
            nonceCount = 255,
            cnonce = "c"
        )

        assertThat(header).contains("username=\"a\\\"b\"")
        assertThat(header).contains("nc=000000ff")
    }

    private fun parse(value: String): DigestChallenge? {
        return DigestChallenge.strongestOf(parseChallenges(listOf(value)))
    }

    private fun rfcAuthorization(challenge: String): String {
        return parse(challenge)!!.authorization(
            username = "Mufasa",
            password = "Circle of Life",
            method = "GET",
            uri = "/dir/index.html",
            nonceCount = 1,
            cnonce = "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ"
        )
    }
}
