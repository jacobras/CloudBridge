package nl.jacobras.cloudbridge

import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService

internal val CloudService.name: String
    get() = when (this) {
        is DropboxService -> "Dropbox"
        is GoogleDriveService -> "Google Drive"
        is OneDriveService -> "OneDrive"
        else -> "Unknown"
    }