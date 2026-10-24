package nl.jacobras.cloudbridge.demo.ui

/**
 * Whether [saveFile] does anything on this platform.
 */
internal expect val isFileSaveSupported: Boolean

/**
 * Lets the user save [bytes] as a local file, suggesting [name] as file name.
 */
internal expect fun saveFile(name: String, bytes: ByteArray)