package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.PkceAuthenticator
import nl.jacobras.cloudbridge.persistence.Settings

internal class GoogleDrivePkceAuthenticator(
    private val api: GoogleDriveApi,
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String,
    codeVerifier: String
) : PkceAuthenticator(
    clientId = clientId,
    redirectUri = redirectUri,
    codeVerifier = codeVerifier
) {
    override val baseUrl = "https://accounts.google.com/o/oauth2/v2/auth"
    override val scope = "https://www.googleapis.com/auth/drive.appdata"

    override suspend fun exchangeCodeForToken(code: String): String {
        try {
            val token = api.getToken(
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri,
                code = code,
                codeVerifier = codeVerifier
            )
            Settings.googleDriveToken = token.accessToken
            return token.accessToken
        } finally {
            Settings.codeVerifier = null
        }
    }
}