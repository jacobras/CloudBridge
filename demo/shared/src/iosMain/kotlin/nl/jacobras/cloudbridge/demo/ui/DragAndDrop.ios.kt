package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual val isFileDropSupported: Boolean = false

@Composable
internal actual fun Modifier.fileDropTarget(
    onHoverChange: (Boolean) -> Unit,
    onDrop: (List<LocalFile>) -> Unit
): Modifier = this