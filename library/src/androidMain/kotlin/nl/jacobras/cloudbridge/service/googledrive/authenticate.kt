package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.persistence.librarySettings
import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Builds the authorization URL for Google Drive. Open it in a Custom Tab (or browser) to start the
 * PKCE flow.
 */
public fun GoogleDriveService.authenticate(
    clientId: String,
    redirectUri: String
): String {
    val codeVerifier = librarySettings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        librarySettings.codeVerifier = verifier
        verifier
    }
    val authenticator = GoogleDrivePkceAuthenticator(
        api = api,
        clientId = clientId,
        clientSecret = null, // Android apps use SHA-1/package binding instead
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.buildPkceUri()
}