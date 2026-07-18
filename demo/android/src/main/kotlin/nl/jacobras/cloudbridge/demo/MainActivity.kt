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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
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

    private val viewModel = DemoViewModel()

    private var authenticatingProvider: Provider? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val googleDriveAuthenticator = GoogleDriveAuthenticator(
        activity = this,
        onSuccess = { token ->
            DemoSettings.googleDriveToken = token
            viewModel.updateTokens()
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
                    launchAuth = { provider, url ->
                        authenticatingProvider = provider
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
        val provider = authenticatingProvider ?: return@launch
        when (provider) {
            Provider.Dropbox -> {
                val token = viewModel.dropbox.completeAuthentication(
                    clientId = BuildConfig.DROPBOX_CLIENT_ID,
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                DemoSettings.dropboxToken = token
                viewModel.updateTokens()
            }

            Provider.OneDrive -> {
                val oneDriveService = viewModel.oneDrive
                val token = oneDriveService.completeAuthentication(
                    clientId = BuildConfig.ONEDRIVE_CLIENT_ID,
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                DemoSettings.oneDriveToken = token
                viewModel.updateTokens()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoApp(
    viewModel: DemoViewModel,
    launchAuth: (Provider, String) -> Unit,
    onAuthenticateGoogleDrive: () -> Unit
) {
    DemoScreen(
        viewModel = viewModel,
        onAuthenticate = { service ->
            when (service) {
                is DropboxService -> {
                    launchAuth(
                        Provider.Dropbox,
                        service.authenticate(
                            BuildConfig.DROPBOX_CLIENT_ID,
                            REDIRECT_URI
                        )
                    )
                }
                is GoogleDriveService -> {
                    onAuthenticateGoogleDrive()
                }
                is OneDriveService -> {
                    launchAuth(
                        Provider.OneDrive,
                        service.authenticate(
                            BuildConfig.ONEDRIVE_CLIENT_ID,
                            REDIRECT_URI
                        )
                    )
                }
            }
        },
        modifier = Modifier.safeDrawingPadding()
    )
}

private enum class Provider { Dropbox, OneDrive }

private const val REDIRECT_URI = "nl.jacobras.cloudbridge.demo://cloudbridge-auth"