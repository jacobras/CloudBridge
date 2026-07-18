package nl.jacobras.cloudbridge.service.dropbox

import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.queryParameter
import nl.jacobras.cloudbridge.auth.startWebFlow
import nl.jacobras.cloudbridge.persistence.librarySettings
import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Runs the Dropbox PKCE sign-in flow. Opens a web modal to do so.
 */
public suspend fun DropboxService.authenticate(
    clientId: String,
    redirectUri: String
): CloudAccessToken? {
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
    val callbackUrl = startWebFlow(
        url = authenticator.buildPkceUri(),
        redirectUri = redirectUri
    ) ?: return null
    val code = callbackUrl.queryParameter("code") ?: return null
    return authenticator.exchangeCodeForToken(code)
}