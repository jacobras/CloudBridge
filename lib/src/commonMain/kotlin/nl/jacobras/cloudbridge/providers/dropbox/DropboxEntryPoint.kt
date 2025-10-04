package nl.jacobras.cloudbridge.providers.dropbox

import nl.jacobras.cloudbridge.CloudService

public object DropboxEntryPoint {

    public fun getAuthenticator(
        clientId: String,
        redirectUri: String,
        codeVerifier: String
    ): DropboxAuthenticator {
        return DropboxAuthenticator(
            clientId = clientId,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )
    }

    public fun getService(
        clientId: String,
        token: String
    ): CloudService {
        return DropboxService(
            clientId = clientId,
            token = token
        )
    }
}