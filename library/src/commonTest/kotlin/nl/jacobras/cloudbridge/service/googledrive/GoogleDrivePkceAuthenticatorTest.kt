package nl.jacobras.cloudbridge.service.googledrive

import assertk.assertThat
import assertk.assertions.contains
import kotlin.test.Test

class GoogleDrivePkceAuthenticatorTest {

    @Test
    fun `authorization URI forces consent to always get a refresh token`() {
        val authenticator = GoogleDrivePkceAuthenticator(
            api = GoogleDriveService(token = null).api,
            clientId = "clientId",
            clientSecret = null,
            redirectUri = "http://localhost:8080",
            codeVerifier = "DdiKuAnfGnotN1d_Qh5xfTcRcz936rrmFqyelltHIaDwgOjP"
        )

        assertThat(authenticator.buildPkceUri()).contains("&prompt=consent")
    }
}