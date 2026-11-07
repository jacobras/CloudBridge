package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.persistence.LocalAuthenticationServer
import nl.jacobras.cloudbridge.persistence.librarySettings
import nl.jacobras.cloudbridge.security.SecurityUtil

public fun GoogleDriveService.authenticate(
    authServer: LocalAuthenticationServer,
    onSuccess: (CloudAccessToken) -> Unit
): String {
    val clientSecret = clientInfo.googleDriveDesktopSecret
    require(!clientSecret.isNullOrEmpty()) { "Client secret is required for Google Drive on desktop" }
    val codeVerifier = librarySettings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        librarySettings.codeVerifier = verifier
        verifier
    }
    val authenticator = GoogleDrivePkceAuthenticator(
        api = api,
        clientId = clientInfo.clientId,
        clientSecret = clientSecret,
        redirectUri = authServer.url,
        codeVerifier = codeVerifier
    )
    authServer.stop()
    authServer.start(service = this, authenticator = authenticator, onSuccess = onSuccess)
    return authServer.url
}