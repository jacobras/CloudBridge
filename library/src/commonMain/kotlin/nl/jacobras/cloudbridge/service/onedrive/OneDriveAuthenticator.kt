package nl.jacobras.cloudbridge.service.onedrive

import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.PkceAuthenticator
import nl.jacobras.cloudbridge.auth.toCloudAccessToken

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

    // `offline_access` is needed to receive a refresh token.
    override val scope = "files.readwrite openid profile email offline_access"

    override val extraParams = mapOf(
        // Always prompt, so user can choose (a different) account
        "prompt" to "select_account"
    )

    override suspend fun requestToken(code: String): CloudAccessToken {
        return api.getToken(
            clientId = clientId,
            redirectUri = redirectUri,
            code = code,
            codeVerifier = codeVerifier
        ).toCloudAccessToken()
    }
}