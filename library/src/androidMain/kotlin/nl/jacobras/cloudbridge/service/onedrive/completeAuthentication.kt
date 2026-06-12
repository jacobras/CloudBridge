package nl.jacobras.cloudbridge.service.onedrive

import android.net.Uri
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.persistence.librarySettings

/**
 * Exchanges the authorization [code], parsed from [intentUri], for an access token.
 *
 * @throws nl.jacobras.cloudbridge.CloudServiceException
 */
public suspend fun OneDriveService.completeAuthentication(
    clientId: String,
    redirectUri: String,
    intentUri: Uri
): CloudAccessToken? {
    val codeVerifier = librarySettings.codeVerifier ?: return null
    val code = intentUri.getQueryParameter("code") ?: return null
    val authenticator = OneDriveAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.exchangeCodeForToken(code)
}