package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.TokenRefresher

/**
 * Google Drive on Android gets tokens from Google Identity Services.
 */
internal actual fun googleDriveTokenRefresher(clientInfo: ClientInfo): TokenRefresher? {
    return GoogleIdentityTokenRefresher()
}