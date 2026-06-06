package nl.jacobras.cloudbridge.demo.persistence

import com.russhwolf.settings.Settings
import com.russhwolf.settings.contains
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import kotlin.time.Duration.Companion.seconds

internal object DemoSettings {
    var dropboxToken: CloudAccessToken?
        get() {
            return if (demoSettings.contains(KEY_DROPBOX_TOKEN)) {
                CloudAccessToken(
                    accessToken = demoSettings.getStringOrNull(KEY_DROPBOX_TOKEN)!!,
                    refreshToken = demoSettings.getStringOrNull(KEY_DROPBOX_REFRESH_TOKEN),
                    expiresIn = demoSettings.getLongOrNull(KEY_DROPBOX_EXPIRES_IN)?.seconds
                )
            } else null
        }
        set(value) {
            if (value != null) {
                demoSettings.putString(KEY_DROPBOX_TOKEN, value.accessToken)
                demoSettings.putString(KEY_DROPBOX_REFRESH_TOKEN, value.refreshToken ?: "")
                demoSettings.putLong(KEY_DROPBOX_EXPIRES_IN, value.expiresIn?.inWholeSeconds ?: 0L)
            } else {
                demoSettings.remove(KEY_DROPBOX_TOKEN)
                demoSettings.remove(KEY_DROPBOX_REFRESH_TOKEN)
                demoSettings.remove(KEY_DROPBOX_EXPIRES_IN)
            }
        }
    var googleDriveToken: CloudAccessToken?
        get() {
            return if (demoSettings.contains(KEY_GOOGLE_DRIVE_TOKEN)) {
                CloudAccessToken(
                    accessToken = demoSettings.getStringOrNull(KEY_GOOGLE_DRIVE_TOKEN)!!,
                    refreshToken = demoSettings.getStringOrNull(KEY_GOOGLE_DRIVE_REFRESH_TOKEN),
                    expiresIn = demoSettings.getLongOrNull(KEY_GOOGLE_DRIVE_EXPIRES_IN)?.seconds
                )
            } else null
        }
        set(value) {
            if (value != null) {
                demoSettings.putString(KEY_GOOGLE_DRIVE_TOKEN, value.accessToken)
                demoSettings.putString(KEY_GOOGLE_DRIVE_REFRESH_TOKEN, value.refreshToken ?: "")
                demoSettings.putLong(KEY_GOOGLE_DRIVE_EXPIRES_IN, value.expiresIn?.inWholeSeconds ?: 0L)
            } else {
                demoSettings.remove(KEY_GOOGLE_DRIVE_TOKEN)
                demoSettings.remove(KEY_GOOGLE_DRIVE_REFRESH_TOKEN)
                demoSettings.remove(KEY_GOOGLE_DRIVE_EXPIRES_IN)
            }
        }
    var oneDriveToken: CloudAccessToken?
        get() {
            return if (demoSettings.contains(KEY_ONEDRIVE_TOKEN)) {
                CloudAccessToken(
                    accessToken = demoSettings.getStringOrNull(KEY_ONEDRIVE_TOKEN)!!,
                    refreshToken = demoSettings.getStringOrNull(KEY_ONEDRIVE_REFRESH_TOKEN),
                    expiresIn = demoSettings.getLongOrNull(KEY_ONEDRIVE_EXPIRES_IN)?.seconds
                )
            } else null
        }
        set(value) {
            if (value != null) {
                demoSettings.putString(KEY_ONEDRIVE_TOKEN, value.accessToken)
                demoSettings.putString(KEY_ONEDRIVE_REFRESH_TOKEN, value.refreshToken ?: "")
                demoSettings.putLong(KEY_ONEDRIVE_EXPIRES_IN, value.expiresIn?.inWholeSeconds ?: 0L)
            } else {
                demoSettings.remove(KEY_ONEDRIVE_TOKEN)
                demoSettings.remove(KEY_ONEDRIVE_REFRESH_TOKEN)
                demoSettings.remove(KEY_ONEDRIVE_EXPIRES_IN)
            }
        }
}

private const val KEY_DROPBOX_TOKEN = "dropboxToken"
private const val KEY_DROPBOX_REFRESH_TOKEN = "dropboxRefreshToken"
private const val KEY_DROPBOX_EXPIRES_IN = "dropboxTokenExpiresIn"

private const val KEY_GOOGLE_DRIVE_TOKEN = "googleDriveToken"
private const val KEY_GOOGLE_DRIVE_REFRESH_TOKEN = "googleDriveRefreshToken"
private const val KEY_GOOGLE_DRIVE_EXPIRES_IN = "googleDriveTokenExpiresIn"

private const val KEY_ONEDRIVE_TOKEN = "oneDriveToken"
private const val KEY_ONEDRIVE_REFRESH_TOKEN = "oneDriveRefreshToken"
private const val KEY_ONEDRIVE_EXPIRES_IN = "oneDriveTokenExpiresIn"

internal expect val demoSettings: Settings