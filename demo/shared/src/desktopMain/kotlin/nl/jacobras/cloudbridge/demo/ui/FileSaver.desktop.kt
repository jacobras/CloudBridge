package nl.jacobras.cloudbridge.demo.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

internal actual val isFileSaveSupported: Boolean = true

internal actual fun saveFile(name: String, bytes: ByteArray) {
    val dialog = FileDialog(null as Frame?, "Save file", FileDialog.SAVE)
    dialog.file = name
    dialog.isVisible = true

    val directory = dialog.directory ?: return
    val fileName = dialog.file ?: return
    File(directory, fileName).writeBytes(bytes)
}