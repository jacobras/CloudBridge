package nl.jacobras.cloudbridge.persistence

internal actual val librarySettings: Settings = InMemorySettings()

private class InMemorySettings(
    override var codeVerifier: String? = null
) : Settings