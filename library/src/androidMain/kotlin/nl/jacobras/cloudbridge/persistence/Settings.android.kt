package nl.jacobras.cloudbridge.persistence

import com.russhwolf.settings.get
import com.russhwolf.settings.set

internal actual val librarySettings: Settings = SharedPreferenceSettings()

internal class SharedPreferenceSettings : Settings {

    private val sharedPrefSettings = com.russhwolf.settings.Settings()

    override var codeVerifier: String?
        get() = sharedPrefSettings[KEY_CODE_VERIFIER]
        set(value) {
            if (value == null) {
                sharedPrefSettings.remove(KEY_CODE_VERIFIER)
            } else {
                sharedPrefSettings.set(KEY_CODE_VERIFIER, value)
            }
        }
}

private const val KEY_CODE_VERIFIER = "codeVerifier"