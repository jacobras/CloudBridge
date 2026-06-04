package nl.jacobras.cloudbridge.service.googledrive

import kotlinx.browser.window
import nl.jacobras.cloudbridge.getHashParam
import nl.jacobras.cloudbridge.persistence.Settings
import kotlin.js.ExperimentalWasmJsInterop

@Suppress("UnusedReceiverParameter")
@OptIn(ExperimentalWasmJsInterop::class)
public fun GoogleDriveService.completeAuthentication(): String? {
    val tokenValue = window.location.getHashParam("access_token")
    Settings.googleDriveToken = tokenValue
    return tokenValue
}