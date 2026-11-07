package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.TokenRefresher

/**
 * Google Drive on web uses the implicit flow, which has no refresh token.
 */
internal actual fun googleDriveTokenRefresher(clientInfo: ClientInfo): TokenRefresher? = null