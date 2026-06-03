package nl.jacobras.cloudbridge.persistence

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings

internal actual val settings: Settings = PreferencesSettings.Factory().create()