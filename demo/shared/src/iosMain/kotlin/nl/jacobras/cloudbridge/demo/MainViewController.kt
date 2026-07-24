package nl.jacobras.cloudbridge.demo

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.demo.shared.BuildConfig
import nl.jacobras.cloudbridge.demo.ui.DemoScreen
import nl.jacobras.cloudbridge.demo.ui.DemoViewModel
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.dropbox.authenticate
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.googledrive.authenticate
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.service.onedrive.authenticate

fun MainViewController() = ComposeUIViewController {
    val viewModel = remember { DemoViewModel() }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        DemoScreen(
            viewModel = viewModel,
            onAuthenticate = { service ->
                when (service) {
                    is DropboxService -> scope.launch {
                        service.authenticate(
                            clientId = BuildConfig.DROPBOX_CLIENT_ID,
                            redirectUri = REDIRECT_URI
                        )?.let { token ->
                            DemoSettings.dropboxToken = token
                            viewModel.updateTokens()
                        }
                    }

                    is GoogleDriveService -> scope.launch {
                        service.authenticate(
                            clientId = BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                            redirectUri = REDIRECT_URI
                        )?.let { token ->
                            DemoSettings.googleDriveToken = token
                            viewModel.updateTokens()
                        }
                    }

                    is OneDriveService -> scope.launch {
                        service.authenticate(
                            clientId = BuildConfig.ONEDRIVE_CLIENT_ID,
                            redirectUri = REDIRECT_URI
                        )?.let { token ->
                            DemoSettings.oneDriveToken = token
                            viewModel.updateTokens()
                        }
                    }

                    else -> Unit
                }
            },
            modifier = Modifier.safeDrawingPadding()
        )
    }
}

private const val REDIRECT_URI = "nl.jacobras.cloudbridge.demo://cloudbridge-auth"