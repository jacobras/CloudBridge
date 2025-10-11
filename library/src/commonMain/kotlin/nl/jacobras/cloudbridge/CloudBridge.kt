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

    public fun dropbox(clientId: String): DropboxService = DropboxService(clientId)
    public fun googleDrive(clientId: String): GoogleDriveService = GoogleDriveService(clientId)
    public fun oneDrive(clientId: String): OneDriveService = OneDriveService(clientId)
}