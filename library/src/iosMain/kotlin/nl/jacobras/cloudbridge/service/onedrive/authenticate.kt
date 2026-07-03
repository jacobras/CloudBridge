package nl.jacobras.cloudbridge.service.onedrive

import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.queryParameter
import nl.jacobras.cloudbridge.auth.startWebFlow
import nl.jacobras.cloudbridge.persistence.librarySettings
import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Runs the OneDrive PKCE sign-in flow. Opens a web modal to do so.
 */
public suspend fun OneDriveService.authenticate(
    clientId: String,
    redirectUri: String
): CloudAccessToken? {
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
    val callbackUrl = startWebFlow(
        url = authenticator.buildPkceUri(),
        redirectUri = redirectUri
    ) ?: return null
    val code = callbackUrl.queryParameter("code") ?: return null
    return authenticator.exchangeCodeForToken(code)
}