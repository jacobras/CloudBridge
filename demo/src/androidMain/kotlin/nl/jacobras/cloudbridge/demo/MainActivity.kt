package nl.jacobras.cloudbridge.demo

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.demo.ui.FileRow
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.dropbox.authenticate
import nl.jacobras.cloudbridge.service.dropbox.completeAuthentication
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.googledrive.authenticate
import nl.jacobras.cloudbridge.service.googledrive.completeAuthentication
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.service.onedrive.authenticate
import nl.jacobras.cloudbridge.service.onedrive.completeAuthentication

class MainActivity : ComponentActivity() {

    private var pendingUri by mutableStateOf<Uri?>(null)

    private val dropboxService = CloudBridge.dropbox(DemoSettings.dropboxToken)
    private val googleDriveService = CloudBridge.googleDrive(DemoSettings.googleDriveToken)
    private val oneDriveService = CloudBridge.oneDrive(DemoSettings.oneDriveToken)

    private var authenticatingProvider: Provider? = null
    private val scope = CoroutineScope(Dispatchers.Main)

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
                    dropboxService = dropboxService,
                    googleDriveService = googleDriveService,
                    oneDriveService = oneDriveService,
                    launchAuth = { provider, url ->
                        authenticatingProvider = provider
                        CustomTabsIntent.Builder().build().launchUrl(this, url.toUri())
                    }
                )
            }
        }
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
                val token = dropboxService.completeAuthentication(
                    clientId = DROPBOX_CLIENT_ID,
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                DemoSettings.dropboxToken = token
            }

            Provider.GoogleDrive -> {
                val token = googleDriveService.completeAuthentication(
                    clientId = GOOGLE_DRIVE_CLIENT_ID,
                    clientSecret = AndroidMainBuildConfig.DRIVE_DESKTOP_SECRET,
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                DemoSettings.googleDriveToken = token
            }

            Provider.OneDrive -> {
                val token = oneDriveService.completeAuthentication(
                    clientId = ONEDRIVE_CLIENT_ID,
                    redirectUri = REDIRECT_URI,
                    intentUri = uri
                ) ?: return@launch
                DemoSettings.oneDriveToken = token
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoApp(
    dropboxService: DropboxService,
    googleDriveService: GoogleDriveService,
    oneDriveService: OneDriveService,
    launchAuth: (Provider, String) -> Unit
) {
    // Bump to recreate services after obtaining a new token.
    var tokenVersion by remember { mutableIntStateOf(0) }

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
            ServiceSection(
                name = "Dropbox",
                service = dropboxService,
                onAuthenticate = {
                    launchAuth(
                        Provider.Dropbox,
                        dropboxService.authenticate(
                            DROPBOX_CLIENT_ID,
                            REDIRECT_URI
                        )
                    )
                },
                onLogOut = { DemoSettings.dropboxToken = null; tokenVersion++ }
            )
            ServiceSection(
                name = "Google Drive",
                service = googleDriveService,
                onAuthenticate = {
                    launchAuth(
                        Provider.GoogleDrive,
                        googleDriveService.authenticate(
                            clientId = GOOGLE_DRIVE_CLIENT_ID,
                            clientSecret = AndroidMainBuildConfig.DRIVE_DESKTOP_SECRET,
                            redirectUri = REDIRECT_URI
                        )
                    )
                },
                onLogOut = { DemoSettings.googleDriveToken = null; tokenVersion++ }
            )
            ServiceSection(
                name = "OneDrive",
                service = oneDriveService,
                onAuthenticate = {
                    launchAuth(
                        Provider.OneDrive,
                        oneDriveService.authenticate(
                            ONEDRIVE_CLIENT_ID,
                            REDIRECT_URI
                        )
                    )
                },
                onLogOut = { DemoSettings.oneDriveToken = null; tokenVersion++ }
            )
        }
    }
}

@Composable
private fun ServiceSection(
    name: String,
    service: CloudService,
    onAuthenticate: () -> Unit,
    onLogOut: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.headlineSmall)

        if (!service.isAuthenticated()) {
            Button(onClick = onAuthenticate) { Text("Authenticate with $name") }
            return@Column
        }

        val userInfo by produceState<UserInfo?>(null, service) {
            value = try {
                service.getUserInfo()
            } catch (e: Exception) {
                null
            }
        }
        if (userInfo != null) {
            SelectionContainer { Text("Authenticated as: $userInfo") }
        }
        Button(onClick = onLogOut) { Text("Log out") }

        var error by remember { mutableStateOf("") }
        val files by produceState(emptyList(), service) {
            value = try {
                service.listFiles(FolderPath("/")).also { error = "" }
            } catch (e: Exception) {
                error = e.message.toString()
                emptyList()
            }
        }

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

private enum class Provider { Dropbox, GoogleDrive, OneDrive }

private const val REDIRECT_URI = "nl.jacobras.cloudbridge.demo://cloudbridge-auth"

private const val DROPBOX_CLIENT_ID = "nw5f95uw77yrz3j"
private const val GOOGLE_DRIVE_CLIENT_ID =
    "218224394553-ls5llp4qcqlem66ovl0rp871jlq47m21.apps.googleusercontent.com"
private const val ONEDRIVE_CLIENT_ID = "40916102-96a6-46ca-929e-90cc62c3be9a"