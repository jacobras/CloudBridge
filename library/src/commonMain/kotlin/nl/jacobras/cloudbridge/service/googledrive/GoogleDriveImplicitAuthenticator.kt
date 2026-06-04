package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.ImplicitAuthenticator

/**
 * Google Drive only supports Implicit Flow
 * (https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow).
 *
 * If you try to use PKCE, you'll get an error because it requires client_secret (which is against
 * the OAuth 2.0 spec for public clients).
 */
internal class GoogleDriveImplicitAuthenticator(
    val clientId: String,
    val redirectUri: String
) : ImplicitAuthenticator(
    clientId = clientId,
    redirectUri = redirectUri
) {
    override val baseUrl = "https://accounts.google.com/o/oauth2/v2/auth"
    override val scope = "https://www.googleapis.com/auth/drive.appdata"
}