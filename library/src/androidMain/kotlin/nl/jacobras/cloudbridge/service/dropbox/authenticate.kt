package nl.jacobras.cloudbridge.service.dropbox

import nl.jacobras.cloudbridge.persistence.librarySettings
import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Builds the authorization URL for Dropbox. Open it in a Custom Tab (or browser) to start the
 * PKCE flow.
 */
public fun DropboxService.authenticate(
    clientId: String,
    redirectUri: String
): String {
    val codeVerifier = librarySettings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        librarySettings.codeVerifier = verifier
        verifier
    }
    val authenticator = DropboxAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.buildPkceUri()
}