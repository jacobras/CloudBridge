package nl.jacobras.cloudbridge.service.dropbox

import kotlinx.browser.window
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil

public fun DropboxService.startAuthenticationByRedirect(
    redirectUri: String,
    clientId: String
) {
    val codeVerifier = Settings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        Settings.codeVerifier = verifier
        verifier
    }
    val authenticator = DropboxAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    val uri = authenticator.buildUri()
    window.location.href = uri
}