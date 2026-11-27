package nl.jacobras.cloudbridge.service.dropbox

import kotlinx.browser.window
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.persistence.librarySettings
import org.w3c.dom.url.URLSearchParams

/**
 * Exchanges the authorization code, parsed from the current URL, for an access token.
 *
 * @return the token, or `null` if there's no pending authentication to complete.
 * @throws CloudBridgeException
 */
@OptIn(ExperimentalWasmJsInterop::class)
public suspend fun DropboxService.completeAuthentication(
    redirectUri: String
): CloudAccessToken? {
    val params = URLSearchParams(window.location.search.toJsString())
    val code = params.get("code") ?: return null
    val codeVerifier = librarySettings.codeVerifier ?: return null
    val authenticator = DropboxAuthenticator(
        api = api,
        clientId = clientInfo.clientId,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier
    )
    return authenticator.exchangeCodeForToken(code)
}