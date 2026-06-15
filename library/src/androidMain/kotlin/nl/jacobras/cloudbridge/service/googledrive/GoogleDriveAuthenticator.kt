package nl.jacobras.cloudbridge.service.googledrive

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken

/**
 * Starts Google Drive authorization flow through Google Identity Services.
 *
 * Needs to be instantiated before `onResume`, because it registers an activity result listener
 * internally.
 *
 * @param onSuccess Called when a [CloudAccessToken] was successfully obtained.
 * @param onDenied Called when the user denies or dismisses the flow.
 * @param onFailure Called when an error occurs.
 */
public class GoogleDriveAuthenticator(
    private val activity: ComponentActivity,
    private val onSuccess: (CloudAccessToken) -> Unit,
    private val onDenied: () -> Unit,
    private val onFailure: (Throwable) -> Unit
) {
    private val consentLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            onDenied()
            return@registerForActivityResult
        }
        val authorization = try {
            completeAuthentication(result.data)
        } catch (e: CloudServiceException) {
            onFailure(e)
            return@registerForActivityResult
        }
        handle(authorization)
    }

    /**
     * Starts the authorization flow, showing a consent screen when the user has not granted access
     * yet. The outcome is passed to [onSuccess], [onDenied] and [onFailure].
     */
    public fun authenticate() {
        Identity.getAuthorizationClient(activity)
            .authorize(buildAuthorizationRequest())
            .addOnSuccessListener { result -> handle(result.toModel()) }
            .addOnFailureListener { onFailure(CloudServiceException.Unknown(it)) }
    }

    private fun handle(authorization: GoogleAuthorization) {
        when (authorization) {
            is GoogleAuthorization.Authorized -> onSuccess(authorization.token)
            is GoogleAuthorization.ConsentRequired -> consentLauncher.launch(
                IntentSenderRequest.Builder(authorization.pendingIntent.intentSender).build()
            )
            GoogleAuthorization.Denied -> onDenied()
        }
    }

    private fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
    }

    /**
     * Parses the [intent] returned to the activity after the authorization consent screen and
     * returns the resulting [GoogleAuthorization].
     *
     * @throws CloudServiceException when the authorization result could not be read.
     */
    private fun completeAuthentication(
        intent: Intent?
    ): GoogleAuthorization {
        val result = try {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent)
        } catch (e: ApiException) {
            throw CloudServiceException.Unknown(e)
        }
        return result.toModel()
    }

    /**
     * @return [GoogleAuthorization.ConsentRequired] if user needs to consent,
     * [GoogleAuthorization.Authorized] if everything's good to go, or [GoogleAuthorization.Denied].
     */
    private fun AuthorizationResult.toModel(): GoogleAuthorization {
        val pendingIntent = pendingIntent
        if (hasResolution() && pendingIntent != null) {
            return GoogleAuthorization.ConsentRequired(pendingIntent)
        }
        val accessToken = accessToken ?: return GoogleAuthorization.Denied
        return GoogleAuthorization.Authorized(CloudAccessToken(accessToken = accessToken))
    }
}

private const val DRIVE_APPDATA_SCOPE: String = "https://www.googleapis.com/auth/drive.appdata"