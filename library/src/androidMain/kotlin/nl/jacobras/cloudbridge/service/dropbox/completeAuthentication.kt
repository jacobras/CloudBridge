package nl.jacobras.cloudbridge.service.dropbox

import android.net.Uri
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.persistence.librarySettings

/**
 * Exchanges the authorization [code], parsed from [intentUri], for an access token.
 *
 * @throws CloudServiceException
 */
public suspend fun DropboxService.completeAuthentication(
    clientId: String,
    redirectUri: String,
    intentUri: Uri
): CloudAccessToken? {
    val codeVerifier = librarySettings.codeVerifier ?: return null
    val code = intentUri.getQueryParameter("code") ?: return null
    val authenticator = DropboxAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.exchangeCodeForToken(code)
}