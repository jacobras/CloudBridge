package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.PkceAuthenticator
import nl.jacobras.cloudbridge.auth.toCloudAccessToken
import nl.jacobras.cloudbridge.persistence.librarySettings

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

    init {
        require(clientSecret.isNotEmpty()) { "Client secret cannot be empty" }
    }

    override suspend fun exchangeCodeForToken(code: String): CloudAccessToken {
        try {
            val token = api.getToken(
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri,
                code = code,
                codeVerifier = codeVerifier
            )
            return token.toCloudAccessToken()
        } finally {
            librarySettings.codeVerifier = null
        }
    }
}