package nl.jacobras.cloudbridge.provider.googledrive

import net.thauvin.erik.urlencoder.UrlEncoderUtil
import nl.jacobras.cloudbridge.CloudAuthenticator
import nl.jacobras.cloudbridge.persistence.Settings

internal class GoogleDriveAuthenticator(
    private val api: GoogleDriveApi,
    private val clientId: String,
    private val redirectUri: String,
    codeVerifier: String
) : CloudAuthenticator(codeVerifier = codeVerifier) {

    override fun buildUri(): String {
        val encodedRedirectUri = UrlEncoderUtil.encode(redirectUri)

        return buildString {
            append("https://accounts.google.com/o/oauth2/v2/auth")
            append("?client_id=$clientId")
            append("&scope=https://www.googleapis.com/auth/drive.appdata")
            append("&response_type=code")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=S256")
            append("&redirect_uri=$encodedRedirectUri")
        }
    }

    override suspend fun exchangeCodeForToken(code: String) {
        return try {
            val token = api.getToken(
                clientId = clientId,
                redirectUri = redirectUri,
                code = code,
                codeVerifier = codeVerifier
            )
            Settings.googleDriveToken = token.accessToken
        } finally {
            Settings.codeVerifier = null
        }
    }
}