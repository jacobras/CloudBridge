package nl.jacobras.cloudbridge

import nl.jacobras.cloudbridge.logging.EmptyLogger
import nl.jacobras.cloudbridge.logging.Logger
import nl.jacobras.cloudbridge.providers.dropbox.DropboxEntryPoint
import nl.jacobras.cloudbridge.providers.googledrive.GoogleDriveEntryPoint
import nl.jacobras.cloudbridge.providers.onedrive.OneDriveEntryPoint

public object CloudBridge {
    public var logger: Logger = EmptyLogger

    public val dropbox: DropboxEntryPoint = DropboxEntryPoint
    public val googleDrive: GoogleDriveEntryPoint = GoogleDriveEntryPoint
    public val oneDrive: OneDriveEntryPoint = OneDriveEntryPoint
}