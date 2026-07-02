package nl.jacobras.cloudbridge.demo.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import nl.jacobras.cloudbridge.demo.BuildConfig
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
                            clientId = BuildConfig.DROPBOX_CLIENT_ID,
                            onSuccess = { token ->
                                DemoSettings.dropboxToken = token
                                viewModel.updateTokens()
                            }
                        )
                        uriHandler.openUri(url)
                    }
                    is GoogleDriveService -> {
                        val url = service.authenticate(
                            authServer = localServer,
                            clientId = BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                            clientSecret = BuildConfig.GOOGLE_DRIVE_CLIENT_SECRET,
                            onSuccess = { token ->
                                DemoSettings.googleDriveToken = token
                                viewModel.updateTokens()
                            }
                        )
                        uriHandler.openUri(url)
                    }
                    is OneDriveService -> {
                        val url = service.authenticate(
                            authServer = localServer,
                            clientId = BuildConfig.ONEDRIVE_CLIENT_ID,
                            onSuccess = { token ->
                                DemoSettings.oneDriveToken = token
                                viewModel.updateTokens()
                            }
                        )
                        uriHandler.openUri(url)
                    }
                }
            }
        )
    }
}