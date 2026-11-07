package nl.jacobras.cloudbridge.demo

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.ui.DemoScreen
import nl.jacobras.cloudbridge.demo.ui.DemoViewModel
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.dropbox.authenticate
import nl.jacobras.cloudbridge.service.dropbox.completeAuthentication
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveAuthenticator
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.service.onedrive.authenticate
import nl.jacobras.cloudbridge.service.onedrive.completeAuthentication

internal class MainActivity : ComponentActivity() {

    private val viewModel = DemoViewModel(
        dropboxClientId = BuildConfig.DROPBOX_CLIENT_ID,
        // Google Drive on Android doesn't need a client ID.
        googleDriveClientId = "",
        oneDriveClientId = BuildConfig.ONEDRIVE_CLIENT_ID
    )

    private var authenticatingService: CloudService? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val googleDriveAuthenticator = GoogleDriveAuthenticator(
        activity = this,
        onSuccess = { token ->
            viewModel.connectGoogleDrive(token)
        },
        onDenied = {},
        onFailure = { error ->
            val errorMessage = "Google Drive sign-in failed: ${error.message}"
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        intent?.data?.let {
            completeAuthentication(it)
            intent = null
        }

        setContent {
            MaterialTheme {
                DemoApp(
                    viewModel = viewModel,
                    launchAuth = { service, url ->
                        authenticatingService = service
                        CustomTabsIntent.Builder().build().launchUrl(this, url.toUri())
                    },
                    onAuthenticateGoogleDrive = googleDriveAuthenticator::authenticate
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        intent.data?.let {
            completeAuthentication(it)
        }
    }

    private fun completeAuthentication(uri: Uri) = scope.launch {
        when (val service = authenticatingService) {
            is DropboxService -> {
                val token = service.completeAuthentication(
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                viewModel.connectDropbox(token)
            }

            is OneDriveService -> {
                val token = service.completeAuthentication(
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                viewModel.connectOneDrive(token)
            }

            else -> Unit
        }
    }
}

@Composable
private fun DemoApp(
    viewModel: DemoViewModel,
    launchAuth: (CloudService, String) -> Unit,
    onAuthenticateGoogleDrive: () -> Unit
) {
    DemoScreen(
        viewModel = viewModel,
        onAuthenticate = { service ->
            when (service) {
                is DropboxService -> {
                    launchAuth(service, service.authenticate(REDIRECT_URI))
                }
                is GoogleDriveService -> {
                    onAuthenticateGoogleDrive()
                }
                is OneDriveService -> {
                    launchAuth(service, service.authenticate(REDIRECT_URI))
                }
            }
        },
        modifier = Modifier.safeDrawingPadding()
    )
}

private const val REDIRECT_URI = "nl.jacobras.cloudbridge.demo://cloudbridge-auth"