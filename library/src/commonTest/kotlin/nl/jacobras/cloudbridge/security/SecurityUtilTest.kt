package nl.jacobras.cloudbridge.security

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class SecurityUtilTest {

    @Test
    fun `build code challenge`() {
        val codeVerifier = "DdiKuAnfGnotN1d_Qh5xfTcRcz936rrmFqyelltHIaDwgOjP"

        val challenge = SecurityUtil.buildCodeChallenge(codeVerifier)
        assertThat(challenge).isEqualTo("cbRw1o0E9AbKSTzqEMLFi0CPhrwracNFSB-jcMrRgP4")
    }
}