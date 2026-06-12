package nl.jacobras.cloudbridge.service.googledrive

@Suppress("UnusedReceiverParameter")
public fun GoogleDriveService.authenticate(
    redirectUri: String,
    clientId: String
): String {
    val authenticator = GoogleDriveImplicitAuthenticator(clientId, redirectUri)
    return authenticator.buildImplicitFlowUri()
}