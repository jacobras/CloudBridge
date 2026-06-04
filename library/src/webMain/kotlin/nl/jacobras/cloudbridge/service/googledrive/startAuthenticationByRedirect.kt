package nl.jacobras.cloudbridge.service.googledrive

import kotlinx.browser.window

@Suppress("UnusedReceiverParameter")
public fun GoogleDriveService.startAuthenticationByRedirect(
    redirectUri: String,
    clientId: String
) {
    val authenticator = GoogleDriveImplicitAuthenticator(clientId, redirectUri)
    val uri = authenticator.buildUri()
    window.location.href = uri
}