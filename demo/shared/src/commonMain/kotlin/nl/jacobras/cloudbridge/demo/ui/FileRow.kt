package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FilePath
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.Instant

/**
 * Shows [item]. Right-click (or long-press on touch devices) opens a menu to rename or delete it.
 */
@Composable
fun FileRow(
    item: CloudItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Last press position, used to show the menu where the user clicked.
    var pressPosition by remember { mutableStateOf(Offset.Zero) }

    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Press) continue
                            pressPosition = event.changes.first().position
                            if (event.buttons.isSecondaryPressed) {
                                menuOpen = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                )
        ) {
            Icon(
                imageVector = when (item) {
                    is CloudFolder -> Icons.Outlined.Folder
                    is CloudFile -> Icons.Outlined.FilePresent
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(8.dp))

            Column {
                Text(
                    text = item.path.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val text = when (item) {
                    is CloudFile -> {
                        val fileSize = HumanReadable.fileSize(item.sizeInBytes)
                        val timeAgo = HumanReadable.timeAgo(item.modified)
                        "${item.name} ($fileSize, $timeAgo)"
                    }
                    is CloudFolder -> item.name
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Zero-size anchor at the press position, so the menu opens there.
        Box(Modifier.offset { pressPosition.round() }) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PreviewFolder() {
    FileRow(
        modifier = Modifier.padding(8.dp),
        item = CloudFolder(
            id = CloudItemId("preview"),
            path = FolderPath("/preview"),
            name = "Preview"
        ),
        onClick = {},
        onRename = {},
        onDelete = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PreviewFile() {
    FileRow(
        modifier = Modifier.padding(8.dp),
        item = CloudFile(
            id = CloudItemId("preview"),
            path = FilePath("/preview"),
            name = "Preview",
            sizeInBytes = 12345,
            modified = Instant.parse("2026-02-12T00:00:00Z"),
            revision = "1"
        ),
        onClick = {},
        onRename = {},
        onDelete = {}
    )
}
