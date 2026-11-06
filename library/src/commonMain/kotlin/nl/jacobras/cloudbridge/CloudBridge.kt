package nl.jacobras.cloudbridge

import io.ktor.client.HttpClient
import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.internal.CloudBridgeInternals
import nl.jacobras.cloudbridge.internal.InternalCloudBridgeApi
import nl.jacobras.cloudbridge.logging.EmptyLogger
import nl.jacobras.cloudbridge.logging.Logger
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.service.webdav.WebDavCredentials
import nl.jacobras.cloudbridge.service.webdav.WebDavService

/**
 * CloudBridge: Multiple clouds, one Kotlin Multiplatform bridge.
 * This is the main entry point.
 */
public object CloudBridge {
    public var logger: Logger = EmptyLogger

    @OptIn(InternalCloudBridgeApi::class)
    internal val defaultHttpClient by lazy {
        HttpClient {
            with(CloudBridgeInternals) { applyDefaults() }
            CloudBridgeInternals.extraHttpClientConfig(this)
        }
    }

    /**
     * Instance of the Dropbox API.
     *
     * @param clientId Client ID, as registered in the Dropbox App Console (see docs).
     * @param token The access token to use for authentication. Leave empty if not yet authenticated.
     * @param onTokenRefreshed Callback to persist the new token after a refresh.
     */
    public fun dropbox(
        clientId: String,
        token: CloudAccessToken? = null,
        onTokenRefreshed: ((CloudAccessToken) -> Unit)? = null
    ): DropboxService = DropboxService(
        clientInfo = ClientInfo(clientId),
        token = token,
        onTokenRefreshed = onTokenRefreshed
    )

    /**
     * Instance of the Google Drive API.
     *
     * @param clientId Client ID, as registered in the Google Cloud Console (see docs). Not used on
     * Android, as Google Identity Services identifies the app by its package name.
     * @param desktopClientSecret The client secret, only needed on desktop.
     * @param token The access token to use for authentication. Leave empty if not yet authenticated.
     * @param onTokenRefreshed Callback to persist the new token after a refresh.
     */
    public fun googleDrive(
        clientId: String,
        desktopClientSecret: String? = null,
        token: CloudAccessToken? = null,
        onTokenRefreshed: ((CloudAccessToken) -> Unit)? = null
    ): GoogleDriveService = GoogleDriveService(
        clientInfo = ClientInfo(clientId, desktopClientSecret),
        token = token,
        onTokenRefreshed = onTokenRefreshed
    )

    /**
     * Instance of the OneDrive API.
     *
     * @param clientId Client ID, as registered in Microsoft Entra admin center (see docs).
     * @param token The access token to use for authentication. Leave empty if not yet authenticated.
     * @param onTokenRefreshed Callback to persist the new token after a refresh.
     */
    public fun oneDrive(
        clientId: String,
        token: CloudAccessToken? = null,
        onTokenRefreshed: ((CloudAccessToken) -> Unit)? = null
    ): OneDriveService = OneDriveService(
        clientInfo = ClientInfo(clientId),
        token = token,
        onTokenRefreshed = onTokenRefreshed
    )

    /**
     * Instance of a WebDAV server.
     *
     * @param credentials The credentials to use for authentication.
     */
    public fun webDav(
        credentials: WebDavCredentials
    ): WebDavService = WebDavService(credentials)
}