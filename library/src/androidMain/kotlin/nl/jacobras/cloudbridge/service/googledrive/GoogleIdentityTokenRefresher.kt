package nl.jacobras.cloudbridge.service.googledrive

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
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
        val result = authorize(context)
        val accessToken = result.accessToken
        if (result.hasResolution() || accessToken == null) {
            // Consent needed (e.g. access revoked), which can't be shown from here.
            throw CloudServiceException.NotAuthenticatedException()
        }
        if (accessToken == outdated.accessToken) {
            // Google Identity Services handed back the token that was just rejected.
            throw CloudServiceException.NotAuthenticatedException()
        }
        return CloudAccessToken(accessToken = accessToken)
    }

    private suspend fun authorize(context: Context): AuthorizationResult {
        return suspendCancellableCoroutine { continuation ->
            Identity.getAuthorizationClient(context)
                .authorize(buildGoogleDriveAuthorizationRequest())
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }
}