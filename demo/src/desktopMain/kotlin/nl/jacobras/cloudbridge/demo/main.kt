package nl.jacobras.cloudbridge.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.demo.ui.FileRow
import nl.jacobras.cloudbridge.demo.ui.MainViewModel
import nl.jacobras.cloudbridge.persistence.LocalAuthenticationServer
import nl.jacobras.cloudbridge.service.dropbox.authenticate
import nl.jacobras.cloudbridge.service.googledrive.authenticate
import nl.jacobras.cloudbridge.service.onedrive.authenticate
import kotlin.time.Duration.Companion.milliseconds

fun main() = application {
    val viewModel = remember { MainViewModel() }

    var obtainedToken by remember { mutableStateOf<CloudAccessToken?>(null) }
    var selectedService by remember { mutableStateOf<CloudService>(viewModel.dropbox) }

    var showSuccessDialog by remember { mutableStateOf(false) }
    val localServer = remember { LocalAuthenticationServer() }

    val allFiles by viewModel.files.collectAsState()
    val files = allFiles[selectedService] ?: emptyList()

    val allServiceErrors by viewModel.serviceErrors.collectAsState()
    val error = allServiceErrors[selectedService] ?: ""

    DisposableEffect(Unit) {
        onDispose {
            localServer.stop()
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CloudBridge Demo",
    ) {
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                confirmButton = {
                    Button(onClick = { showSuccessDialog = false }) {
                        Text("OK")
                    }
                },
                text = { SelectionContainer { Text("Success! Token: $obtainedToken") } }
            )
        }

        val uriHandler = LocalUriHandler.current
        val scope = rememberCoroutineScope()

        fun openDelayed(url: String) = scope.launch {
            delay(300.milliseconds)
            uriHandler.openUri(url)
        }

        Column {
            Text("Sign in with: (opens browser)")
            Row {
                val dropboxService = viewModel.dropbox
                Button(onClick = {
                    val url = dropboxService.authenticate(
                        authServer = localServer,
                        clientId = "nw5f95uw77yrz3j",
                        onSuccess = {
                            obtainedToken = it
                            selectedService = dropboxService
                            DemoSettings.dropboxToken = it
                            showSuccessDialog = true
                        }
                    )
                    openDelayed(url)
                }) { Text("Dropbox") }
                Button(onClick = {
                    val googleDriveService = viewModel.googleDrive
                    val url = googleDriveService.authenticate(
                        authServer = localServer,
                        clientId = "218224394553-ls5llp4qcqlem66ovl0rp871jlq47m21.apps.googleusercontent.com",
                        clientSecret = DesktopMainBuildConfig.DRIVE_DESKTOP_SECRET,
                        onSuccess = {
                            obtainedToken = it
                            selectedService = googleDriveService
                            DemoSettings.googleDriveToken = it
                            showSuccessDialog = true
                        }
                    )
                    openDelayed(url)
                }) { Text("Google Drive") }
                Button(onClick = {
                    val oneDriveService = viewModel.oneDrive
                    val url = oneDriveService.authenticate(
                        authServer = localServer,
                        clientId = "40916102-96a6-46ca-929e-90cc62c3be9a",
                        onSuccess = {
                            obtainedToken = it
                            selectedService = oneDriveService
                            DemoSettings.oneDriveToken = it
                            showSuccessDialog = true
                        }
                    )
                    openDelayed(url)
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