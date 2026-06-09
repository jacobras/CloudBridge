package nl.jacobras.cloudbridge.service.onedrive

import kotlinx.browser.window
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil

public fun OneDriveService.authenticate(
    redirectUri: String,
    clientId: String
) {
    val codeVerifier = Settings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        Settings.codeVerifier = verifier
        verifier
    }
    val authenticator = OneDriveAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    val uri = authenticator.buildPkceUri()
    window.location.href = uri
}