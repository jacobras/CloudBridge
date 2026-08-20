package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.ui.graphics.vector.ImageVector
import cloudbridge.demo.shared.generated.resources.Res
import cloudbridge.demo.shared.generated.resources.ic_dropbox
import cloudbridge.demo.shared.generated.resources.ic_google_drive
import cloudbridge.demo.shared.generated.resources.ic_one_drive
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.DummyCloudService
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.service.webdav.WebDavService
import org.jetbrains.compose.resources.DrawableResource

internal val CloudService.displayName: String
    get() {
        return when (this) {
            is DropboxService -> "Dropbox"
            is GoogleDriveService -> "Google Drive"
            is OneDriveService -> "OneDrive"
            is WebDavService -> "WebDAV"
            is DummyCloudService -> "Dummy"
            else -> error("Missing name for $this")
        }
    }

internal val AvailableService.displayName: String
    get() {
        return when (this) {
            AvailableService.Dropbox -> "Dropbox"
            AvailableService.GoogleDrive -> "Google Drive"
            AvailableService.OneDrive -> "OneDrive"
            AvailableService.WebDav -> "WebDAV"
        }
    }

internal sealed interface ServiceLogo {
    data class Drawable(val resource: DrawableResource) : ServiceLogo
    data class Vector(val image: ImageVector) : ServiceLogo
}

internal val CloudService.logo: ServiceLogo
    get() {
        return when (this) {
            is DropboxService -> ServiceLogo.Drawable(Res.drawable.ic_dropbox)
            is GoogleDriveService -> ServiceLogo.Drawable(Res.drawable.ic_google_drive)
            is OneDriveService -> ServiceLogo.Drawable(Res.drawable.ic_one_drive)
            is WebDavService -> ServiceLogo.Vector(Icons.Default.Cloud)
            is DummyCloudService -> ServiceLogo.Drawable(Res.drawable.ic_dropbox)
            else -> error("Missing logo for $this")
        }
    }

internal val AvailableService.logo: ServiceLogo
    get() {
        return when (this) {
            AvailableService.Dropbox -> ServiceLogo.Drawable(Res.drawable.ic_dropbox)
            AvailableService.GoogleDrive -> ServiceLogo.Drawable(Res.drawable.ic_google_drive)
            AvailableService.OneDrive -> ServiceLogo.Drawable(Res.drawable.ic_one_drive)
            AvailableService.WebDav -> ServiceLogo.Vector(Icons.Default.Cloud)
        }
    }

/**
 * Creates an unauthenticated instance of this service.
 *
 * Not supported for [AvailableService.WebDav], which requires credentials up front.
 */
internal fun AvailableService.toService(): CloudService {
    return when (this) {
        AvailableService.Dropbox -> CloudBridge.dropbox()
        AvailableService.GoogleDrive -> CloudBridge.googleDrive()
        AvailableService.OneDrive -> CloudBridge.oneDrive()
        AvailableService.WebDav -> error("WebDAV requires credentials, use CloudBridge.webDav()")
    }
}