package nl.jacobras.cloudbridge.service.googledrive

import net.thauvin.erik.urlencoder.UrlEncoderUtil
import nl.jacobras.cloudbridge.auth.ImplicitAuthenticator
import nl.jacobras.cloudbridge.persistence.Settings

/**
 * Google Drive only supports Implicit Flow
 * (https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow).
 */
internal class GoogleDriveAuthenticator(
    private val clientId: String,
    private val redirectUri: String
) : ImplicitAuthenticator {

    override fun buildUri(): String {
        val encodedRedirectUri = UrlEncoderUtil.encode(redirectUri)

        return buildString {
            append("https://accounts.google.com/o/oauth2/v2/auth")
            append("?client_id=$clientId")
            append("&scope=https://www.googleapis.com/auth/drive.appdata")
            append("&response_type=token")
            append("&redirect_uri=$encodedRedirectUri")
        }
    }

    override fun storeToken(token: String) {
        Settings.googleDriveToken = token
        Settings.codeVerifier = null
    }
}