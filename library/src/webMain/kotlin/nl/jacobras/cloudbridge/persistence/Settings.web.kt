package nl.jacobras.cloudbridge.persistence

import kotlinx.browser.window
import org.w3c.dom.get
import org.w3c.dom.set

internal actual val librarySettings: Settings = LocalStorageSettings()

private class LocalStorageSettings : Settings {
    override var codeVerifier: String?
        get() = window.localStorage[KEY_CODE_VERIFIER]
        set(value) {
            if (value == null) {
                window.localStorage.removeItem(KEY_CODE_VERIFIER)
            } else {
                window.localStorage[KEY_CODE_VERIFIER] = value
            }
        }
}

private const val KEY_CODE_VERIFIER = "codeVerifier"