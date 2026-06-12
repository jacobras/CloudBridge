package nl.jacobras.cloudbridge.service.onedrive

import kotlinx.browser.window
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.persistence.librarySettings
import org.w3c.dom.url.URLSearchParams
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsString

@OptIn(ExperimentalWasmJsInterop::class)
public suspend fun OneDriveService.completeAuthentication(
    clientId: String,
    redirectUri: String
): CloudAccessToken? {
    val params = URLSearchParams(window.location.search.toJsString())
    val code = params.get("code") ?: return null
    val codeVerifier = librarySettings.codeVerifier ?: return null
    val authenticator = OneDriveAuthenticator(
        api = api,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.exchangeCodeForToken(code)
}