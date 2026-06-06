package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.persistence.LocalAuthenticationServer
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil

public fun GoogleDriveService.authenticate(
    authServer: LocalAuthenticationServer,
    clientId: String,
    clientSecret: String,
    onSuccess: (CloudAccessToken) -> Unit
): String {
    val codeVerifier = Settings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        Settings.codeVerifier = verifier
        verifier
    }
    val authenticator = GoogleDrivePkceAuthenticator(
        api = api,
        clientId = clientId,
        clientSecret = clientSecret,
        redirectUri = authServer.url,
        codeVerifier = codeVerifier
    )
    authServer.stop()
    authServer.start(service = this, authenticator = authenticator, onSuccess = onSuccess)
    return authServer.url
}