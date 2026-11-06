import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.demo.BuildConfig
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.demo.ui.DemoScreen
import nl.jacobras.cloudbridge.demo.ui.DemoViewModel
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.dropbox.authenticate
import nl.jacobras.cloudbridge.service.dropbox.completeAuthentication
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.googledrive.authenticate
import nl.jacobras.cloudbridge.service.googledrive.completeAuthentication
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.service.onedrive.authenticate
import nl.jacobras.cloudbridge.service.onedrive.completeAuthentication

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        val viewModel = remember {
            DemoViewModel(
                dropboxClientId = BuildConfig.DROPBOX_CLIENT_ID,
                googleDriveClientId = BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                oneDriveClientId = BuildConfig.ONEDRIVE_CLIENT_ID
            )
        }
        val scope = rememberCoroutineScope()

        DemoScreen(
            viewModel = viewModel,
            onAuthenticate = { service ->
                when (service) {
                    is DropboxService -> {
                        window.location.href = service.authenticate(redirectUri = REDIRECT_URI)
                    }
                    is GoogleDriveService -> {
                        window.location.href = service.authenticate(redirectUri = REDIRECT_URI)
                    }
                    is OneDriveService -> {
                        window.location.href = service.authenticate(redirectUri = REDIRECT_URI)
                    }
                }
            },
            onFinishAuthOnWeb = { service ->
                when (service) {
                    is DropboxService -> scope.launch {
                        val token = service.completeAuthentication(redirectUri = REDIRECT_URI)
                        DemoSettings.dropboxToken = token
                        viewModel.refresh()
                    }
                    is GoogleDriveService -> {
                        val token = service.completeAuthentication()
                        DemoSettings.googleDriveToken = token
                        viewModel.refresh()
                    }
                    is OneDriveService -> scope.launch {
                        val token = service.completeAuthentication(redirectUri = REDIRECT_URI)
                        DemoSettings.oneDriveToken = token
                        viewModel.refresh()
                    }
                }
            }
        )
    }
}

private const val REDIRECT_URI = "http://localhost:8080"