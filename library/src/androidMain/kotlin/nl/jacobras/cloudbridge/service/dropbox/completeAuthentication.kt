package nl.jacobras.cloudbridge.service.dropbox

import android.net.Uri
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.persistence.librarySettings

/**
 * Exchanges the authorization [code], parsed from [intentUri], for an access token.
 *
 * @throws CloudBridgeException
 */
public suspend fun DropboxService.completeAuthentication(
    redirectUri: String,
    intentUri: Uri
): CloudAccessToken? {
    val codeVerifier = librarySettings.codeVerifier ?: return null
    val code = intentUri.getQueryParameter("code") ?: return null
    val authenticator = DropboxAuthenticator(
        api = api,
        clientId = clientInfo.clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.exchangeCodeForToken(code)
}