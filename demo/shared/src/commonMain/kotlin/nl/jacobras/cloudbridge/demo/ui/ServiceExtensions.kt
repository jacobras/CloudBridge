package nl.jacobras.cloudbridge.demo.ui

import cloudbridge.demo.shared.generated.resources.Res
import cloudbridge.demo.shared.generated.resources.ic_dropbox
import cloudbridge.demo.shared.generated.resources.ic_google_drive
import cloudbridge.demo.shared.generated.resources.ic_one_drive
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import org.jetbrains.compose.resources.DrawableResource

internal val CloudService.name: String
    get() {
        return when (this) {
            is DropboxService -> "Dropbox"
            is GoogleDriveService -> "Google Drive"
            is OneDriveService -> "OneDrive"
            else -> error("Missing name for $this")
        }
    }

internal val CloudService.logo: DrawableResource
    get() {
        return when (this) {
            is DropboxService -> Res.drawable.ic_dropbox
            is GoogleDriveService -> Res.drawable.ic_google_drive
            is OneDriveService -> Res.drawable.ic_one_drive
            else -> error("Missing logo for $this")
        }
    }