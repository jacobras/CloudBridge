package nl.jacobras.cloudbridge

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
     */
    public fun dropbox(): DropboxService = DropboxService()

    /**
     * Instance of the Google Drive API.
     */
    public fun googleDrive(): GoogleDriveService = GoogleDriveService()

    /**
     * Instance of the OneDrive API.
     */
    public fun oneDrive(): OneDriveService = OneDriveService()
}