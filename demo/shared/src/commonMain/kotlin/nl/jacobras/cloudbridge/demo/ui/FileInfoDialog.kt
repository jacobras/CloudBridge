package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.humanreadable.HumanReadable

/**
 * Dialog for non-text files: shows the file's info, a preview for images and a Download button
 * on platforms that can save files.
 */
@Composable
internal fun FileInfoDialog(
    file: CloudFile,
    content: FileContent,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text("Delete") }
                if (isFileSaveSupported) {
                    Button(
                        enabled = !isDownloading,
                        onClick = onDownload
                    ) { Text(if (isDownloading) "Downloading…" else "Download") }
                }
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Close") } },
        title = { Text(file.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (content) {
                    is FileContent.Image -> Image(
                        bitmap = content.bitmap,
                        contentDescription = file.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    )
                    FileContent.Loading -> Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                    else -> Unit
                }
                InfoRow("Path", file.path.toString())
                InfoRow("Size", HumanReadable.fileSize(file.sizeInBytes))
                InfoRow("Modified", file.modified.toString())
                InfoRow("Revision", file.revision)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}