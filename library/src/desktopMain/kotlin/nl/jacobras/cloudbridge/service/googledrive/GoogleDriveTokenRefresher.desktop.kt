package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.OAuthTokenRefresher
import nl.jacobras.cloudbridge.auth.TokenRefresher

internal actual fun googleDriveTokenRefresher(clientInfo: ClientInfo): TokenRefresher? {
    return OAuthTokenRefresher(clientInfo)
}