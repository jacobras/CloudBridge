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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.demo.ui.FileRow
import nl.jacobras.cloudbridge.demo.ui.MainViewModel
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.service.dropbox.authenticate
import nl.jacobras.cloudbridge.service.dropbox.completeAuthentication
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveAuthenticator
import nl.jacobras.cloudbridge.service.onedrive.authenticate
import nl.jacobras.cloudbridge.service.onedrive.completeAuthentication

class MainActivity : ComponentActivity() {

    private val viewModel = MainViewModel()

    private var authenticatingProvider: Provider? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val googleDriveAuthenticator = GoogleDriveAuthenticator(
        activity = this,
        onSuccess = { token ->
            DemoSettings.googleDriveToken = token
            viewModel.refresh(viewModel.googleDrive)
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
                    clientId = DROPBOX_CLIENT_ID,
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                DemoSettings.dropboxToken = token
                viewModel.refresh(viewModel.dropbox)
            }

            Provider.OneDrive -> {
                val oneDriveService = viewModel.oneDrive
                val token = oneDriveService.completeAuthentication(
                    clientId = ONEDRIVE_CLIENT_ID,
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                DemoSettings.oneDriveToken = token
                viewModel.refresh(oneDriveService)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoApp(
    viewModel: MainViewModel,
    launchAuth: (Provider, String) -> Unit,
    onAuthenticateGoogleDrive: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val userInfo by viewModel.userInfo.collectAsState()
    val serviceErrors by viewModel.serviceErrors.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("CloudBridge Demo") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val dropboxService = viewModel.dropbox
            ServiceSection(
                name = "Dropbox",
                service = dropboxService,
                userInfo = userInfo[dropboxService],
                files = files[dropboxService] ?: emptyList(),
                error = serviceErrors[dropboxService] ?: "",
                onAuthenticate = {
                    launchAuth(
                        Provider.Dropbox,
                        viewModel.dropbox.authenticate(
                            DROPBOX_CLIENT_ID,
                            REDIRECT_URI
                        )
                    )
                },
                onLogOut = {
                    DemoSettings.dropboxToken = null
                    viewModel.refresh(dropboxService)
                }
            )
            val googleDriveService = viewModel.googleDrive
            ServiceSection(
                name = "Google Drive",
                service = googleDriveService,
                userInfo = userInfo[googleDriveService],
                files = files[googleDriveService] ?: emptyList(),
                error = serviceErrors[googleDriveService] ?: "",
                onAuthenticate = onAuthenticateGoogleDrive,
                onLogOut = {
                    DemoSettings.googleDriveToken = null
                    viewModel.refresh(googleDriveService)
                }
            )
            val oneDriveService = viewModel.oneDrive
            ServiceSection(
                name = "OneDrive",
                service = oneDriveService,
                userInfo = userInfo[oneDriveService],
                files = files[oneDriveService] ?: emptyList(),
                error = serviceErrors[oneDriveService] ?: "",
                onAuthenticate = {
                    launchAuth(
                        Provider.OneDrive,
                        oneDriveService.authenticate(
                            ONEDRIVE_CLIENT_ID,
                            REDIRECT_URI
                        )
                    )
                },
                onLogOut = {
                    DemoSettings.oneDriveToken = null
                    viewModel.refresh(oneDriveService)
                }
            )
        }
    }
}

@Composable
private fun ServiceSection(
    name: String,
    service: CloudService,
    userInfo: UserInfo?,
    files: List<CloudItem>,
    error: String,
    onAuthenticate: () -> Unit,
    onLogOut: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.headlineSmall)

        if (!service.isAuthenticated()) {
            Button(onClick = onAuthenticate) { Text("Authenticate with $name") }
            return@Column
        }

        if (userInfo != null) {
            SelectionContainer { Text("Authenticated as: $userInfo") }
        }
        Button(onClick = onLogOut) { Text("Log out") }

        if (error.isNotEmpty()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        for (item in files) {
            FileRow(item = item, onClick = {})
            if (item != files.last()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

private enum class Provider { Dropbox, OneDrive }

private const val REDIRECT_URI = "nl.jacobras.cloudbridge.demo://cloudbridge-auth"

private const val DROPBOX_CLIENT_ID = "nw5f95uw77yrz3j"
private const val ONEDRIVE_CLIENT_ID = "40916102-96a6-46ca-929e-90cc62c3be9a"