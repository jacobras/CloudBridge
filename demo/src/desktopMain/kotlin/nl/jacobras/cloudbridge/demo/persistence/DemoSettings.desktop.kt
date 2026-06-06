package nl.jacobras.cloudbridge.demo.persistence

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings

internal actual val demoSettings: Settings = PreferencesSettings.Factory().create()