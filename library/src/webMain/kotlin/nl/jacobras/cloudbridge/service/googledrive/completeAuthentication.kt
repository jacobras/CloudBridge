package nl.jacobras.cloudbridge.service.googledrive

import kotlinx.browser.window
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.getHashParam
import kotlin.js.ExperimentalWasmJsInterop

@Suppress("UnusedReceiverParameter")
@OptIn(ExperimentalWasmJsInterop::class)
public fun GoogleDriveService.completeAuthentication(): CloudAccessToken? {
    val tokenValue = window.location.getHashParam("access_token") ?: return null
    return CloudAccessToken(accessToken = tokenValue)
}