package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FilePath
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.Instant

@Composable
fun FileRow(
    item: CloudItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
        onClick = {}
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
            modified = Instant.parse("2026-02-12T00:00:00Z")
        ),
        onClick = {}
    )
}