@file:OptIn(ExperimentalForeignApi::class)

package nl.jacobras.cloudbridge.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Launches [ASWebAuthenticationSession] for [url]. Suspends until the user completes or cancels
 * the flow.
 *
 * @param redirectUri the redirect URI
 * @return the redirect [NSURL] on success, or `null` if the user canceled the flow.
 * @throws Throwable if the session reports a non-cancellation error.
 */
internal suspend fun startWebFlow(
    url: String,
    redirectUri: String
): NSURL? = withContext(Dispatchers.Main) {
    val authUrl = requireNotNull(NSURL(string = url)) { "Invalid URL: $url" }
    val redirectUriScheme = redirectUri.substringBefore("://")

    suspendCancellableCoroutine { continuation ->
        val contextProvider = PresentationContextProvider()

        val session = ASWebAuthenticationSession(
            uRL = authUrl,
            callbackURLScheme = redirectUriScheme,
            completionHandler = { callbackURL: NSURL?, error: NSError? ->
                when {
                    error != null && error.code == ERROR_LOGIN_CANCELED -> {
                        continuation.resume(null)
                    }
                    error != null -> {
                        continuation.resumeWithException(
                            Throwable("Web authentication failed: ${error.localizedDescription}")
                        )
                    }
                    else -> continuation.resume(callbackURL)
                }
            }
        )
        session.presentationContextProvider = contextProvider
        continuation.invokeOnCancellation { session.cancel() }
        session.start()
    }
}

private class PresentationContextProvider :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {

    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor {
        val application = UIApplication.sharedApplication
        return application.keyWindow
            ?: application.windows.filterIsInstance<UIWindow>().firstOrNull()
            ?: UIWindow()
    }
}

/** `ASWebAuthenticationSessionError.canceledLogin`. **/
private const val ERROR_LOGIN_CANCELED = 1L