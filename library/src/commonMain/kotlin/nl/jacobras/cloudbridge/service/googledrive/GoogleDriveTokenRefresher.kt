package nl.jacobras.cloudbridge.service.googledrive

import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.TokenRefresher

/**
 * @return how Google Drive tokens are refreshed on this platform, or `null` if they can't be.
 */
internal expect fun googleDriveTokenRefresher(clientInfo: ClientInfo): TokenRefresher?