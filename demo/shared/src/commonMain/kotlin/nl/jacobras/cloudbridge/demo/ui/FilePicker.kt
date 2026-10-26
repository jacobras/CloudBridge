package nl.jacobras.cloudbridge.demo.ui

/**
 * Whether [pickFiles] does anything on this platform.
 */
internal expect val isFilePickerSupported: Boolean

/**
 * Lets the user choose one or more files. [onPicked] isn't called if the user cancels.
 * Must be called from a click handler, because browsers only open a picker in response
 * to a user action.
 */
internal expect fun pickFiles(onPicked: (List<LocalFile>) -> Unit)