package nl.jacobras.cloudbridge.service.dropbox

import nl.jacobras.cloudbridge.auth.PkceAuthenticator
import nl.jacobras.cloudbridge.persistence.Settings

internal class DropboxAuthenticator(
    private val api: DropboxApi,
    private val clientId: String,
    private val redirectUri: String,
    codeVerifier: String
) : PkceAuthenticator(
    clientId = clientId,
    redirectUri = redirectUri,
    codeVerifier = codeVerifier
) {
    override val baseUrl = "https://www.dropbox.com/oauth2/authorize"
    override val scope = ""

    override suspend fun exchangeCodeForToken(code: String): String {
        try {
            val token = api.getToken(
                clientId = clientId,
                redirectUri = redirectUri,
                code = code,
                codeVerifier = codeVerifier
            )
            Settings.dropboxToken = token.accessToken
            return token.accessToken
        } finally {
            Settings.codeVerifier = null
        }
    }
}