package nl.jacobras.cloudbridge.service.dropbox

import nl.jacobras.cloudbridge.persistence.librarySettings
import nl.jacobras.cloudbridge.security.SecurityUtil

public fun DropboxService.authenticate(
    redirectUri: String,
    clientId: String
): String {
    val codeVerifier = librarySettings.codeVerifier ?: let {
        val verifier = SecurityUtil.createRandomCodeVerifier()
        librarySettings.codeVerifier = verifier
        verifier
    }
    val authenticator = DropboxAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.buildPkceUri()
}