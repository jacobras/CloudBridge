package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialog used both for creating a folder/file and for editing an existing file.
 *
 * @param showContent Whether to show the (editable) content field. Off for folders.
 * @param nameEditable Whether the name can be changed. Off when editing, since the API
 * only allows updating a file's content.
 * @param onDelete When non-null, a Delete button is shown (used when editing).
 */
@Composable
internal fun EditDialog(
    title: String,
    confirmLabel: String,
    nameEditable: Boolean,
    showContent: Boolean,
    initialName: String = "",
    initialContent: String = "",
    onConfirm: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                Button(
                    enabled = name.isNotBlank(),
                    onClick = { onConfirm(name, content) }
                ) { Text(confirmLabel) }
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    readOnly = !nameEditable,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showContent) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Content") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}