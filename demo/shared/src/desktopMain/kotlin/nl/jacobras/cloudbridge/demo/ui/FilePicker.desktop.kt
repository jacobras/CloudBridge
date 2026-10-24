package nl.jacobras.cloudbridge.demo.ui

internal actual val isFilePickerSupported: Boolean = false

internal actual fun pickFiles(onPicked: (List<LocalFile>) -> Unit) = Unit