package nl.jacobras.cloudbridge.service.googledrive

import android.accounts.Account
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.TokenRefreshEndpoint
import nl.jacobras.cloudbridge.auth.TokenRefresher
import nl.jacobras.cloudbridge.internal.CloudBridgeContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock

/**
 * Gets a new Google Drive access token from Google Identity Services, for tokens obtained through
 * [GoogleDriveAuthenticator]. Works as long as the user has granted access before, otherwise,
 * a [CloudServiceException.NotAuthenticatedException] is thrown and the user needs to
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
            ?: throw CloudServiceException.NotAuthenticatedException()
        // Without the account, a silent authorization could return another account's token.
        val account = outdated.account ?: throw CloudServiceException.NotAuthenticatedException()
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
            // User action required (re-authentication), can't refresh this token.
            throw CloudServiceException.NotAuthenticatedException()
        }
        if (accessToken == outdated.accessToken) {
            // Shouldn't happen, because it's cleared above, but just in case.
            throw CloudServiceException.NotAuthenticatedException()
        }
        return CloudAccessToken(accessToken = accessToken, account = account)
    }
}

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
}