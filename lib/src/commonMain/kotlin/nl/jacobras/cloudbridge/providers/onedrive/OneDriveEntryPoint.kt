package nl.jacobras.cloudbridge.providers.onedrive

import nl.jacobras.cloudbridge.CloudService

public object OneDriveEntryPoint {

    public fun getAuthenticator(
        clientId: String,
        redirectUri: String,
        codeVerifier: String
    ): OneDriveAuthenticator {
        return OneDriveAuthenticator(
            clientId = clientId,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )
    }

    public fun getService(
        clientId: String,
        token: String
    ): CloudService {
        return OneDriveService(
            clientId = clientId,
            token = token
        )
    }
}