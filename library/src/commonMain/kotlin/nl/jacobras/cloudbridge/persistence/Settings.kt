package nl.jacobras.cloudbridge.persistence

import com.russhwolf.settings.Settings

internal object Settings {
    var codeVerifier: String?
        get() = settings.getStringOrNull(KEY_CODE_VERIFIER)
        set(value) {
            if (value != null) {
                settings.putString(KEY_CODE_VERIFIER, value)
            } else {
                settings.remove(KEY_CODE_VERIFIER)
            }
        }

    var dropboxToken: String?
        get() = settings.getStringOrNull(KEY_TOKEN_DROPBOX)
        set(value) {
            if (value != null) {
                settings.putString(KEY_TOKEN_DROPBOX, value)
            } else {
                settings.remove(KEY_TOKEN_DROPBOX)
            }
        }
    var googleDriveToken: String?
        get() = settings.getStringOrNull(KEY_TOKEN_GOOGLE_DRIVE)
        set(value) {
            if (value != null) {
                settings.putString(KEY_TOKEN_GOOGLE_DRIVE, value)
            } else {
                settings.remove(KEY_TOKEN_GOOGLE_DRIVE)
            }
        }
    var oneDriveToken: String?
        get() = settings.getStringOrNull(KEY_TOKEN_ONEDRIVE)
        set(value) {
            if (value != null) {
                settings.putString(KEY_TOKEN_ONEDRIVE, value)
            } else {
                settings.remove(KEY_TOKEN_ONEDRIVE)
            }
        }
}

private const val KEY_CODE_VERIFIER = "codeVerifier"
private const val KEY_TOKEN_DROPBOX = "dropboxToken"
private const val KEY_TOKEN_GOOGLE_DRIVE = "googleDriveToken"
private const val KEY_TOKEN_ONEDRIVE = "oneDriveToken"

internal expect val settings: Settings