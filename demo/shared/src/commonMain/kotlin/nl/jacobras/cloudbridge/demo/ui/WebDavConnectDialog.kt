package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings

/**
 * WebDAV has no OAuth flow, so signing in means entering the server URL and credentials.
 */
@Composable
internal fun WebDavConnectDialog(
    onConfirm: (serverUrl: String, username: String, password: String) -> Unit,
    onDismiss: () -> Unit
) {
    var serverUrl by remember {
        mutableStateOf(DemoSettings.webDavServerUrl ?: "http://localhost")
    }
    var username by remember { mutableStateOf(DemoSettings.webDavUsername.orEmpty()) }
    var password by remember { mutableStateOf(DemoSettings.webDavPassword.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = serverUrl.isNotBlank() && username.isNotBlank(),
                onClick = { onConfirm(serverUrl.trim(), username, password) }
            ) { Text("Connect") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Connect to WebDAV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}