package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files

internal actual val isFileDropSupported: Boolean = true

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.fileDropTarget(
    onHoverChange: (Boolean) -> Unit,
    onDrop: (List<LocalFile>) -> Unit
): Modifier {
    val currentOnHoverChange by rememberUpdatedState(onHoverChange)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = currentOnHoverChange(true)
            override fun onExited(event: DragAndDropEvent) = currentOnHoverChange(false)
            override fun onEnded(event: DragAndDropEvent) = currentOnHoverChange(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files =
                    (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>)
                        .filterIsInstance<File>()
                        .filter { it.isFile }
                if (files.isEmpty()) return false

                currentOnDrop(files.map { it.toLocalFile() })
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
        target = target
    )
}

private fun File.toLocalFile() = LocalFile(
    name = name,
    sizeInBytes = length(),
    mimeType = Files.probeContentType(toPath()),
    open = { inputStream().asSource().buffered() }
)