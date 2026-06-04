package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.ImplicitAuthenticator
import nl.jacobras.cloudbridge.persistence.Settings

/**
 * Google Drive only supports Implicit Flow
 * (https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow).
 */
internal class GoogleDriveAuthenticator(
    val clientId: String,
    val redirectUri: String
) : ImplicitAuthenticator(
    clientId = clientId,
    redirectUri = redirectUri
) {
    override val baseUrl = "https://accounts.google.com/o/oauth2/v2/auth"
    override val scope = "https://www.googleapis.com/auth/drive.appdata"

    override fun storeToken(token: String) {
        Settings.googleDriveToken = token
        Settings.codeVerifier = null
    }
}