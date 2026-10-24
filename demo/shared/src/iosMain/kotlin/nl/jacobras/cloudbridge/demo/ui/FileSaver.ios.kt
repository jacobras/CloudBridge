package nl.jacobras.cloudbridge.demo.ui

internal actual val isFileSaveSupported: Boolean = false

internal actual fun saveFile(name: String, bytes: ByteArray) = Unit