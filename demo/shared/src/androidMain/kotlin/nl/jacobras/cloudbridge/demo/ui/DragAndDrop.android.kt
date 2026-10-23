package nl.jacobras.cloudbridge.demo.ui

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import kotlinx.io.IOException
import kotlinx.io.asSource
import kotlinx.io.buffered

internal actual val isFileDropSupported: Boolean = true

@Composable
internal actual fun Modifier.fileDropTarget(
    onHoverChange: (Boolean) -> Unit,
    onDrop: (List<LocalFile>) -> Unit
): Modifier {
    val context = LocalContext.current
    val currentOnHoverChange by rememberUpdatedState(onHoverChange)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val target = remember(context) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = currentOnHoverChange(true)
            override fun onExited(event: DragAndDropEvent) = currentOnHoverChange(false)
            override fun onEnded(event: DragAndDropEvent) = currentOnHoverChange(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()

                // Needed to read content URIs from other apps.
                context.findActivity()?.requestDragAndDropPermissions(dragEvent)

                val clipData = dragEvent.clipData ?: return false
                val files = (0 until clipData.itemCount)
                    .mapNotNull { clipData.getItemAt(it).uri }
                    .map { it.toLocalFile(context.contentResolver) }
                if (files.isEmpty()) return false

                currentOnDrop(files)
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            val description = event.toAndroidDragEvent().clipDescription
            description != null && (0 until description.mimeTypeCount).any {
                !description.getMimeType(it).startsWith("text/")
            }
        },
        target = target
    )
}

private fun Uri.toLocalFile(contentResolver: ContentResolver): LocalFile {
    var name = lastPathSegment ?: "file"
    var size: Long? = null
    contentResolver.query(this, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    return LocalFile(
        name = name,
        sizeInBytes = size,
        mimeType = contentResolver.getType(this),
        open = {
            val stream =
                contentResolver.openInputStream(this) ?: throw IOException("Can't open $this")
            stream.asSource().buffered()
        }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}