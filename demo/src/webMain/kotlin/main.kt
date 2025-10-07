@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.logging.Logger
import org.w3c.dom.url.URLSearchParams
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsString

private class KermitLogger : Logger {
    override fun log(message: String) {
        co.touchlab.kermit.Logger.d(message)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CloudBridge.logger = KermitLogger()

    val dropboxService = CloudBridge.dropbox(
        clientId = "nw5f95uw77yrz3j"
    )
    val googleDriveService = CloudBridge.googleDrive(
        clientId = "218224394553-sgks2ok1rnh4r1i5ue4stba5dral9v1i.apps.googleusercontent.com"
    )
    val oneDriveService = CloudBridge.oneDrive(
        clientId = "40916102-96a6-46ca-929e-90cc62c3be9a"
    )
    val allServices = listOf(dropboxService, googleDriveService, oneDriveService)

    ComposeViewport {
        val scope = rememberCoroutineScope()

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            var filename by remember { mutableStateOf("") }
            var content by remember { mutableStateOf("") }

            TextField(
                value = filename,
                onValueChange = { filename = it },
                label = { Text("Filename") }
            )
            TextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") }
            )

            Button(
                onClick = {
                    for (service in allServices.filter { it.isAuthenticated() }) {
                        scope.launch {
                            service.createFile(
                                filename = filename,
                                content = content
                            )
                        }
                    }
                }
            ) { Text("Upload file") }

            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudServiceColumn(
                    name = "Dropbox",
                    service = dropboxService,
                    modifier = Modifier.weight(1f)
                )
                CloudServiceColumn(
                    name = "Google Drive",
                    service = googleDriveService,
                    modifier = Modifier.weight(1f)
                )
                CloudServiceColumn(
                    name = "OneDrive",
                    service = oneDriveService,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CloudServiceColumn(
    name: String,
    service: CloudService,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(name)

        if (service.isAuthenticated()) {
            Text("Authenticated!")
            Button(onClick = {
                service.logout()
                window.location.reload()
            }) { Text("Log out") }

            var error by remember { mutableStateOf("") }

            val files by produceState(emptyList()) {
                value = try {
                    service.listFiles().also {
                        error = ""
                    }
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

            Text(
                text = "Files",
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            for (file in files) {
                SelectionContainer {
                    Column {
                        Text(
                            text = file.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${file.name} (${file.sizeInBytes} bytes)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (file != files.last()) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        } else {
            val authenticator = service.getAuthenticator(
                redirectUri = "http://localhost:8080"
            )
            val authenticateUrl = authenticator.buildUri()

            SelectionContainer {
                Text("URL: $authenticateUrl")
            }

            val params = URLSearchParams(window.location.search.toJsString())
            val code = params.get("code")

            if (code != null) {
                Text("Code: $code")
                val scope = rememberCoroutineScope()

                Button(
                    onClick = {
                        scope.launch {
                            authenticator.exchangeCodeForToken(code = code)
                            window.location.href = window.location.origin + window.location.pathname
                        }
                    }
                ) { Text("Request token using code") }
            }

            val uriHandler = LocalUriHandler.current
            Button(
                onClick = { uriHandler.openUri(authenticateUrl) }
            ) {
                Text("Authenticate with $name")
            }
        }
    }
}