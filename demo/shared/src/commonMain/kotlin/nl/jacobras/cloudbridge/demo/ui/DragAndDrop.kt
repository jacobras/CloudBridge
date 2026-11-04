package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Whether [fileDropTarget] does anything on this platform.
 */
internal expect val isFileDropSupported: Boolean

/**
 * Accepts files dragged in from outside the app. Does nothing on platforms
 * where [isFileDropSupported] is false.
 *
 * @param onHoverChange Called with true while files are dragged over this target.
 * @param onDrop Called with the dropped files.
 */
@Composable
internal expect fun Modifier.fileDropTarget(
    onHoverChange: (Boolean) -> Unit,
    onDrop: (List<LocalFile>) -> Unit
): Modifier