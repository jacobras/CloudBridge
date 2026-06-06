package nl.jacobras.cloudbridge.demo.persistence

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

internal actual val demoSettings: Settings = PreferencesSettings.Factory(
    rootPreferences = Preferences.userRoot().node("nl/jacobras/cloudbridge/demo")
).create()