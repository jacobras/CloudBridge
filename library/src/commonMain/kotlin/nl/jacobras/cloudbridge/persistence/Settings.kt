package nl.jacobras.cloudbridge.persistence

internal interface Settings {
    var codeVerifier: String?
}

internal expect val librarySettings: Settings