package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.model.CloudItem

@Composable
internal fun FileDetailDialog(
    service: CloudService,
    content: String,
    item: CloudItem,
    onDismiss: () -> Unit
) {
    // TODO: load file content in here

    val scope = rememberCoroutineScope()
    if (content.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            service.delete(item.id)
                            onDismiss()
                        } catch (e: CloudServiceException) {
                            Logger.e(e) { "Failed to delete ${item.name}" }
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { Button(onClick = onDismiss) { Text("Close") } },
            title = {
                SelectionContainer {
                    Text("${item.name} (${item.id})")
                }
            },
            text = {
                SelectionContainer {
                    Text(content)
                }
            }
        )
    }
}