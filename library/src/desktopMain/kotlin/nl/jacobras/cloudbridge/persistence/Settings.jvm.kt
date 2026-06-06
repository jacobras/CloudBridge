package nl.jacobras.cloudbridge.persistence

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

internal actual val settings: Settings = PreferencesSettings.Factory(
    rootPreferences = Preferences.userRoot().node("nl/jacobras/cloudbridge")
).create()