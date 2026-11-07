package nl.jacobras.cloudbridge.auth

/**
 * Holds [clientId] and (only for Google Drive on desktop) the [googleDriveDesktopSecret].
 */
internal data class ClientInfo(
    val clientId: String,
    val googleDriveDesktopSecret: String? = null
)