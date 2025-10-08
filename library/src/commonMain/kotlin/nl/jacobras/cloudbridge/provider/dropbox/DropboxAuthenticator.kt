package nl.jacobras.cloudbridge.provider.dropbox

import net.thauvin.erik.urlencoder.UrlEncoderUtil
import nl.jacobras.cloudbridge.auth.PkceAuthenticator
import nl.jacobras.cloudbridge.persistence.Settings

internal class DropboxAuthenticator(
    private val api: DropboxApi,
    private val clientId: String,
    private val redirectUri: String,
    codeVerifier: String
) : PkceAuthenticator(codeVerifier) {

    override fun buildUri(): String {
        val encodedRedirectUri = UrlEncoderUtil.encode(redirectUri)

        return buildString {
            append("https://www.dropbox.com/oauth2/authorize")
            append("?client_id=$clientId")
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
            Settings.dropboxToken = token.accessToken
        } finally {
            Settings.codeVerifier = null
        }
    }
}