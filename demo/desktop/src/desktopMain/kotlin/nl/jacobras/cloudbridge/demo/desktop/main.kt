package nl.jacobras.cloudbridge.demo.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import nl.jacobras.cloudbridge.demo.DesktopMainBuildConfig
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.demo.ui.DemoScreen
import nl.jacobras.cloudbridge.demo.ui.DemoViewModel
import nl.jacobras.cloudbridge.persistence.LocalAuthenticationServer
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.dropbox.authenticate
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.googledrive.authenticate
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.service.onedrive.authenticate

fun main() = application {
    val viewModel = remember { DemoViewModel() }

    var showSuccessDialog by remember { mutableStateOf(false) }
    val localServer = remember { LocalAuthenticationServer() }

    DisposableEffect(Unit) {
        onDispose {
            localServer.stop()
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CloudBridge Demo",
        state = rememberWindowState(size = DpSize(1000.dp, 600.dp))
    ) {
        val uriHandler = LocalUriHandler.current

        DemoScreen(
            viewModel = viewModel,
            onAuthenticate = { service ->
                when (service) {
                    is DropboxService -> {
                        val url = service.authenticate(
                            authServer = localServer,
                            clientId = "nw5f95uw77yrz3j",
                            onSuccess = { token ->
                                DemoSettings.dropboxToken = token
                                showSuccessDialog = true
                                viewModel.updateTokens()
                            }
                        )
                        uriHandler.openUri(url)
                    }
                    is GoogleDriveService -> {
                        val url = service.authenticate(
                            authServer = localServer,
                            clientId = "218224394553-ls5llp4qcqlem66ovl0rp871jlq47m21.apps.googleusercontent.com",
                            clientSecret = DesktopMainBuildConfig.DRIVE_DESKTOP_SECRET,
                            onSuccess = { token ->
                                DemoSettings.googleDriveToken = token
                                showSuccessDialog = true
                                viewModel.updateTokens()
                            }
                        )
                        uriHandler.openUri(url)
                    }
                    is OneDriveService -> {
                        val url = service.authenticate(
                            authServer = localServer,
                            clientId = "40916102-96a6-46ca-929e-90cc62c3be9a",
                            onSuccess = { token ->
                                DemoSettings.oneDriveToken = token
                                showSuccessDialog = true
                                viewModel.updateTokens()
                            }
                        )
                        uriHandler.openUri(url)
                    }
                }
            },
            onDeauthenticate = { service ->
                when (service) {
                    is DropboxService -> {
                        DemoSettings.dropboxToken = null
                        viewModel.updateTokens()
                    }
                    is GoogleDriveService -> {
                        DemoSettings.googleDriveToken = null
                        viewModel.updateTokens()
                    }
                    is OneDriveService -> {
                        DemoSettings.oneDriveToken = null
                        viewModel.updateTokens()
                    }
                }
            }
        )
    }
}