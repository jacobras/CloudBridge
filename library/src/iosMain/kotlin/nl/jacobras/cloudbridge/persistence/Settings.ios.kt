package nl.jacobras.cloudbridge.persistence

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

internal actual val librarySettings: Settings = IosSettings()

@OptIn(ExperimentalSettingsImplementation::class)
private class IosSettings : Settings {

    private val settings = KeychainSettings()

    override var codeVerifier: String?
        get() = settings[KEY_CODE_VERIFIER]
        set(value) {
            if (value == null) {
                settings.remove(KEY_CODE_VERIFIER)
            } else {
                settings.set(KEY_CODE_VERIFIER, value)
            }
        }
}

private const val KEY_CODE_VERIFIER = "codeVerifier"