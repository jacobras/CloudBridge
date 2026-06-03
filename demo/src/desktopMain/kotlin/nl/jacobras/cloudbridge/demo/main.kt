package nl.jacobras.cloudbridge.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.ui.FileRow
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.persistence.LocalAuthenticationServer

fun main() = application {
    val dropboxService = remember {
        CloudBridge.dropbox()
    }
    val googleDriveService = remember { CloudBridge.googleDrive() }
    val oneDriveService = remember { CloudBridge.oneDrive() }
    var selectedService by remember { mutableStateOf(dropboxService) }

    var obtainedToken by remember { mutableStateOf("") }
    val localServer = remember { LocalAuthenticationServer(onSuccess = { obtainedToken = it }) }

    var error by remember { mutableStateOf("") }
    val files by produceState(emptyList(), obtainedToken) {
        value = try {
            selectedService.listFiles(FolderPath("/")).also {
                error = ""
            }
        } catch (e: Exception) {
            error = e.message.toString()
            emptyList()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            localServer.stop()
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CloudBridge Demo",
    ) {
        if (obtainedToken.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { obtainedToken = "" },
                confirmButton = {
                    Button(onClick = { obtainedToken = "" }) {
                        Text("OK")
                    }
                },
                text = { Text("Success! Token: $obtainedToken") }
            )
        }

        val uriHandler = LocalUriHandler.current
        val scope = rememberCoroutineScope()

        fun selectService(service: CloudService, clientId: String) = scope.launch {
            localServer.stop()
            localServer.start(service = service, clientId = clientId)
            delay(300)
            uriHandler.openUri(localServer.url)
        }

        Column {
            Text("Sign in with: (opens browser)")
            Row {
                Button(onClick = {
                    selectService(
                        service = dropboxService,
                        clientId = "nw5f95uw77yrz3j"
                    )
                }) { Text("Dropbox") }
                Button(onClick = {
                    selectService(
                        service = googleDriveService,
                        clientId = "218224394553-ls5llp4qcqlem66ovl0rp871jlq47m21.apps.googleusercontent.com"
                    )
                }) { Text("Google Drive") }
                Button(onClick = {
                    selectService(
                        service = oneDriveService,
                        clientId = "40916102-96a6-46ca-929e-90cc62c3be9a"
                    )
                }) { Text("OneDrive") }
            }

            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            for (file in files) {
                FileRow(file, onClick = {})
            }
        }
    }
}