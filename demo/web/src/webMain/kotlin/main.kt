@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import co.touchlab.kermit.Logger
import kotlinx.browser.window
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
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
import kotlin.js.ExperimentalWasmJsInterop

private class KermitLogger : nl.jacobras.cloudbridge.logging.Logger {
    override fun log(message: String) {
        Logger.d(message)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CloudBridge.logger = KermitLogger()

    ComposeViewport {
        val viewModel = remember { DemoViewModel() }
        val scope = rememberCoroutineScope()

        DemoScreen(
            viewModel = viewModel,
            onAuthenticate = { service ->
                when (service) {
                    is DropboxService -> {
                        val uri = service.authenticate(
                            clientId = "nw5f95uw77yrz3j",
                            redirectUri = "http://localhost:8080"
                        )
                        window.location.href = uri
                    }
                    is GoogleDriveService -> {
                        val uri = service.authenticate(
                            clientId = "218224394553-hd5j48a5uk9mjec0oq38ctijmpfq0krm.apps.googleusercontent.com",
                            redirectUri = "http://localhost:8080"
                        )
                        window.location.href = uri
                    }
                    is OneDriveService -> {
                        val uri = service.authenticate(
                            clientId = "40916102-96a6-46ca-929e-90cc62c3be9a",
                            redirectUri = "http://localhost:8080"
                        )
                        window.location.href = uri
                    }
                }
            },
            onFinishAuthOnWeb = { service ->
                when (service) {
                    is DropboxService -> scope.launch {
                        val token = service.completeAuthentication(
                            clientId = "nw5f95uw77yrz3j",
                            redirectUri = "http://localhost:8080"
                        )
                        DemoSettings.dropboxToken = token
                        viewModel.updateTokens()
                    }
                    is GoogleDriveService -> {
                        val token = service.completeAuthentication()
                        DemoSettings.googleDriveToken = token
                        viewModel.updateTokens()
                    }
                    is OneDriveService -> scope.launch {
                        val token = service.completeAuthentication(
                            clientId = "40916102-96a6-46ca-929e-90cc62c3be9a",
                            redirectUri = "http://localhost:8080"
                        )
                        DemoSettings.oneDriveToken = token
                        viewModel.updateTokens()
                    }
                }
            }
        )
    }
}