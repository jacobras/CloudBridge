package nl.jacobras.cloudbridge.providers.onedrive

import net.thauvin.erik.urlencoder.UrlEncoderUtil
import nl.jacobras.cloudbridge.CloudAuthenticator
import nl.jacobras.cloudbridge.persistence.Settings

internal class OneDriveAuthenticator(
    private val api: OneDriveApi,
    private val clientId: String,
    private val redirectUri: String,
    codeVerifier: String
) : CloudAuthenticator(codeVerifier = codeVerifier) {

    override fun buildUri(): String {
        val encodedRedirectUri = UrlEncoderUtil.encode(redirectUri)

        return buildString {
            append("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
            append("?client_id=$clientId")
            append("&scope=files.readwrite")
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
            Settings.oneDriveToken = token.accessToken
        } finally {
            Settings.codeVerifier = null
        }
    }
}