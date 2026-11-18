package nl.jacobras.cloudbridge.service.googledrive

import android.accounts.Account
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.TokenRefreshEndpoint
import nl.jacobras.cloudbridge.auth.TokenRefresher
import nl.jacobras.cloudbridge.internal.CloudBridgeContext
import kotlin.time.Clock

/**
 * Gets a new Google Drive access token from Google Identity Services, for tokens obtained through
 * [GoogleDriveAuthenticator]. Works as long as the user has granted access before, otherwise,
 * a [CloudBridgeException.NotAuthenticatedException] is thrown and the user needs to
 * re-authenticate through [GoogleDriveAuthenticator.authenticate].
 *
 * Doesn't do preemptive refresh, because expiration time is not returned by Google Drive.
 */
internal class GoogleIdentityTokenRefresher : TokenRefresher {

    override suspend fun refresh(
        outdated: CloudAccessToken,
        endpoint: TokenRefreshEndpoint,
        clock: Clock
    ): CloudAccessToken {
        // Set at app start by CloudBridgeInitializer; missing if androidx.startup is disabled.
        val context = CloudBridgeContext.applicationContext
            ?: throw notAuthenticated("No application context (is androidx.startup disabled?)")
        // Without the account, a silent authorization could return another account's token.
        val account = outdated.account
            ?: throw notAuthenticated("Token has no account, so it can't be refreshed silently")
        val client = Identity.getAuthorizationClient(context)

        // AuthorizationClient caches tokens, so first clear it so it won't return the token
        // we already have here.
        client.clearToken(
            ClearTokenRequest.builder().setToken(outdated.accessToken).build()
        ).await()

        val result = client.authorize(
            buildGoogleDriveAuthorizationRequest(
                account = Account(account, GOOGLE_ACCOUNT_TYPE)
            )
        ).await()
        val accessToken = result.accessToken
        if (result.hasResolution() || accessToken == null) {
            throw notAuthenticated("Google Identity Services needs user action to authorize")
        }
        if (accessToken == outdated.accessToken) {
            // Shouldn't happen, because it's cleared above, but just in case.
            throw notAuthenticated("Google Identity Services returned the outdated token again")
        }
        return CloudAccessToken(accessToken = accessToken, account = account)
    }

    private fun notAuthenticated(reason: String): CloudBridgeException.NotAuthenticatedException {
        return CloudBridgeException.NotAuthenticatedException(IllegalStateException(reason))
    }
}