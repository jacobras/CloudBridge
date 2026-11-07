package nl.jacobras.cloudbridge.service.googledrive

public fun GoogleDriveService.authenticate(
    redirectUri: String
): String {
    val authenticator = GoogleDriveImplicitAuthenticator(clientInfo.clientId, redirectUri)
    return authenticator.buildImplicitFlowUri()
}