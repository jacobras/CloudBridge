package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.persistence.librarySettings
import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Builds the authorization URL for Google Drive. Open it in a Custom Tab (or browser) to start the
 * PKCE flow.
 *
 * Google requires a [clientSecret] even for the PKCE flow. This is acceptable for installed apps
 * per Google's own documentation.
 */
public fun GoogleDriveService.authenticate(
    clientId: String,
    clientSecret: String,
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
        clientSecret = clientSecret,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.buildPkceUri()
}