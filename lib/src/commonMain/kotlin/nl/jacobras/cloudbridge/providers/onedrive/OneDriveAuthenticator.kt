package nl.jacobras.cloudbridge.providers.onedrive

import net.thauvin.erik.urlencoder.UrlEncoderUtil
import nl.jacobras.cloudbridge.CloudAuthenticator
import nl.jacobras.cloudbridge.security.SecurityUtil

public class OneDriveAuthenticator(
    private val clientId: String,
    private val redirectUri: String,
    codeVerifier: String
) : CloudAuthenticator(
    codeVerifier = codeVerifier.ifEmpty { SecurityUtil.createRandomCodeVerifier() }
) {
    private val service = OneDriveService(
        clientId = clientId,
        token = ""
    )

    public fun buildUrl(): String {
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

    public suspend fun getToken(
        redirectUri: String,
        code: String
    ): String {
        return service.getToken(
            redirectUri = redirectUri,
            code = code,
            codeVerifier = codeVerifier
        )
    }
}