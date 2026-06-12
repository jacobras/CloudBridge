package nl.jacobras.cloudbridge.service.onedrive

import nl.jacobras.cloudbridge.persistence.librarySettings
import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Builds the authorization URL for OneDrive. Open it in a Custom Tab (or browser) to start the
 * PKCE flow.
 */
public fun OneDriveService.authenticate(
    clientId: String,
    redirectUri: String
): String {
    val codeVerifier = librarySettings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        librarySettings.codeVerifier = verifier
        verifier
    }
    val authenticator = OneDriveAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.buildPkceUri()
}