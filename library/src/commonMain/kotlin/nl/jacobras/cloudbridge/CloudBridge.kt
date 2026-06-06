package nl.jacobras.cloudbridge

import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.logging.EmptyLogger
import nl.jacobras.cloudbridge.logging.Logger
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService

/**
 * CloudBridge: Multiple clouds, one Kotlin Multiplatform bridge.
 * This is the main entry point.
 */
public object CloudBridge {
    public var logger: Logger = EmptyLogger

    /**
     * Instance of the Dropbox API.
     *
     * @param token The access token to use for authentication. Leave empty to authenticate a new account.
     */
    public fun dropbox(token: CloudAccessToken? = null): DropboxService = DropboxService(token)

    /**
     * Instance of the Google Drive API.
     *
     * @param token The access token to use for authentication. Leave empty to authenticate a new account.
     */
    public fun googleDrive(token: CloudAccessToken? = null): GoogleDriveService = GoogleDriveService(token)

    /**
     * Instance of the OneDrive API.
     *
     * @param token The access token to use for authentication. Leave empty to authenticate a new account.
     */
    public fun oneDrive(token: CloudAccessToken? = null): OneDriveService = OneDriveService(token)
}