package nl.jacobras.cloudbridge.service.onedrive

import nl.jacobras.cloudbridge.persistence.LocalAuthenticationServer
import nl.jacobras.cloudbridge.persistence.Settings
import nl.jacobras.cloudbridge.security.SecurityUtil

public fun OneDriveService.authenticate(
    authServer: LocalAuthenticationServer,
    clientId: String
): String {
    val codeVerifier = Settings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        Settings.codeVerifier = verifier
        verifier
    }
    val authenticator = OneDriveAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = authServer.url,
        codeVerifier = codeVerifier
    )
    authServer.stop()
    authServer.start(service = this, authenticator = authenticator)
    return authServer.url
}