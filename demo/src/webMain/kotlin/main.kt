@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.logging.Logger
import org.w3c.dom.get
import org.w3c.dom.set
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

    ComposeViewport {
        Row(Modifier.fillMaxSize()) {
            DropboxColumn(Modifier.weight(1f))
            OneDriveColumn(Modifier.weight(1f))
            GoogleDriveColumn(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DropboxColumn(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Dropbox")

        val dropboxToken = window.localStorage["dropboxToken"]
        val dropboxClientId = "nw5f95uw77yrz3j"
        val redirectUri = "http://localhost:8080"

        if (dropboxToken != null) {
            Text("Dropbox: authenticated!")
            Button(onClick = {
                window.localStorage.removeItem("dropboxToken")
                window.location.reload()
            }) { Text("Log out") }

            val service = CloudBridge.dropbox.getService(
                clientId = dropboxClientId,
                token = dropboxToken
            )

            val files by produceState(emptyList()) {
                value = try {
                    service.listFiles()
                } catch (e: Exception) {
                    listOf("Error: ${e.message}")
                }
            }

            Text("Files:")
            for (file in files) {
                Text(file)
            }
        } else {
            val authenticator = CloudBridge.dropbox.getAuthenticator(
                clientId = dropboxClientId,
                redirectUri = redirectUri,
                codeVerifier = window.localStorage["dropboxCodeVerifier"] ?: ""
            )
            val authenticateUrl = authenticator.buildUrl()
            window.localStorage["dropboxCodeVerifier"] = authenticator.codeVerifier

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
                            val token = authenticator.getToken(
                                redirectUri = redirectUri,
                                code = code
                            )
                            window.localStorage["dropboxToken"] = token
                            window.localStorage.removeItem("dropboxCodeVerifier")
                            window.location.href = window.location.origin + window.location.pathname
                        }
                    }
                ) { Text("Request Dropbox token using code") }
            }

            val uriHandler = LocalUriHandler.current
            Button(
                onClick = { uriHandler.openUri(authenticateUrl) }
            ) {
                Text("Authenticate with Dropbox")
            }
        }
    }
}

@Composable
private fun OneDriveColumn(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("OneDrive")

        val oneDriveToken = window.localStorage["oneDriveToken"]
        val oneDriveClientId = "40916102-96a6-46ca-929e-90cc62c3be9a"
        val redirectUri = "http://localhost:8080"

        if (oneDriveToken != null) {
            Text("OneDrive: authenticated!")
            Button(onClick = {
                window.localStorage.removeItem("oneDriveToken")
                window.location.reload()
            }) { Text("Log out") }

            val service = CloudBridge.oneDrive.getService(
                clientId = oneDriveClientId,
                token = oneDriveToken
            )

            val files by produceState(emptyList()) {
                value = try {
                    service.listFiles()
                } catch (e: Exception) {
                    listOf("Error: ${e.message}")
                }
            }

            Text("Files:")
            for (file in files) {
                Text(file)
            }
        } else {
            val authenticator = CloudBridge.oneDrive.getAuthenticator(
                clientId = oneDriveClientId,
                redirectUri = redirectUri,
                codeVerifier = window.localStorage["oneDriveCodeVerifier"] ?: ""
            )
            val authenticateUrl = authenticator.buildUrl()
            window.localStorage["oneDriveCodeVerifier"] = authenticator.codeVerifier

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
                            val token = authenticator.getToken(
                                redirectUri = redirectUri,
                                code = code
                            )
                            window.localStorage["oneDriveToken"] = token
                            window.localStorage.removeItem("oneDriveCodeVerifier")
                            window.location.href = window.location.origin + window.location.pathname
                        }
                    }
                ) { Text("Request OneDrive token using code") }
            }

            val uriHandler = LocalUriHandler.current
            Button(
                onClick = { uriHandler.openUri(authenticateUrl) }
            ) {
                Text("Authenticate with OneDrive")
            }
        }
    }
}

@Composable
private fun GoogleDriveColumn(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Google Drive")

        val googleDriveToken = window.localStorage["googleDriveToken"]
        val googleDriveClientId = "218224394553-sgks2ok1rnh4r1i5ue4stba5dral9v1i.apps.googleusercontent.com"
        val redirectUri = "http://localhost:8080"

        if (googleDriveToken != null) {
            Text("Google Drive: authenticated!")
            Button(onClick = {
                window.localStorage.removeItem("googleDriveToken")
                window.location.reload()
            }) { Text("Log out") }

            val service = CloudBridge.googleDrive.getService(
                clientId = googleDriveClientId,
                token = googleDriveToken
            )

            val files by produceState(emptyList()) {
                value = try {
                    service.listFiles()
                } catch (e: Exception) {
                    listOf("Error: ${e.message}")
                }
            }

            Text("Files:")
            for (file in files) {
                Text(file)
            }
        } else {
            val authenticator = CloudBridge.googleDrive.getAuthenticator(
                clientId = googleDriveClientId,
                redirectUri = redirectUri,
                codeVerifier = window.localStorage["googleDriveCodeVerifier"] ?: ""
            )
            val authenticateUrl = authenticator.buildUrl()
            window.localStorage["googleDriveCodeVerifier"] = authenticator.codeVerifier

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
                            val token = authenticator.getToken(
                                redirectUri = redirectUri,
                                code = code
                            )
                            window.localStorage["googleDriveToken"] = token
                            window.localStorage.removeItem("googleDriveCodeVerifier")
                            window.location.href = window.location.origin + window.location.pathname
                        }
                    }
                ) { Text("Request Google Drive token using code") }
            }

            val uriHandler = LocalUriHandler.current
            Button(
                onClick = { uriHandler.openUri(authenticateUrl) }
            ) {
                Text("Authenticate with Google Drive")
            }
        }
    }
}