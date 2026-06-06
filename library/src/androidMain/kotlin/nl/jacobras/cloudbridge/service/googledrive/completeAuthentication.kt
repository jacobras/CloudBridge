package nl.jacobras.cloudbridge.service.googledrive

import android.net.Uri
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.persistence.Settings

/**
 * Exchanges the authorization [code], parsed from [intentUri], for an access token.
 *
 * @throws CloudServiceException
 */
public suspend fun GoogleDriveService.completeAuthentication(
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    intentUri: Uri
): CloudAccessToken? {
    val codeVerifier = Settings.codeVerifier ?: return null
    val code = intentUri.getQueryParameter("code") ?: return null
    val authenticator = GoogleDrivePkceAuthenticator(
        api = api,
        clientId = clientId,
        clientSecret = clientSecret,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.exchangeCodeForToken(code)
}