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
}

private const val KEY_CODE_VERIFIER = "codeVerifier"

internal expect val settings: Settings