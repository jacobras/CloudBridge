package nl.jacobras.cloudbridge.providers.googledrive

import nl.jacobras.cloudbridge.CloudService

public object GoogleDriveEntryPoint {

    public fun getAuthenticator(
        clientId: String,
        redirectUri: String,
        codeVerifier: String
    ): GoogleDriveAuthenticator {
        return GoogleDriveAuthenticator(
            clientId = clientId,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )
    }

    public fun getService(
        clientId: String,
        token: String
    ): CloudService {
        return GoogleDriveService(
            clientId = clientId,
            token = token
        )
    }
}