package nl.jacobras.cloudbridge.service.onedrive

import nl.jacobras.cloudbridge.auth.PkceAuthenticator
import nl.jacobras.cloudbridge.persistence.Settings

internal class OneDriveAuthenticator(
    private val api: OneDriveApi,
    private val clientId: String,
    private val redirectUri: String,
    codeVerifier: String
) : PkceAuthenticator(
    clientId = clientId,
    redirectUri = redirectUri,
    codeVerifier = codeVerifier
) {
    override val baseUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    override val scope = "files.readwrite openid profile email"

    override suspend fun exchangeCodeForToken(code: String): String {
        try {
            val token = api.getToken(
                clientId = clientId,
                redirectUri = redirectUri,
                code = code,
                codeVerifier = codeVerifier
            )
            Settings.oneDriveToken = token.accessToken
            return token.accessToken
        } finally {
            Settings.codeVerifier = null
        }
    }
}