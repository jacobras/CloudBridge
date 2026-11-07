package nl.jacobras.cloudbridge.service.googledrive

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge.logger
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.auth.ClientInfo
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
            onNotOkResult(result.data)
            return@registerForActivityResult
        }
        val authorization = try {
            completeAuthentication(result.data)
        } catch (e: CloudBridgeException) {
            logger.log("Failed to complete Google Drive authentication: $e")
            onFailure(e)
            return@registerForActivityResult
        }
        handle(authorization)
    }

    /**
     * Starts the authorization flow. Always shows an account picker, so this can also be used to
     * add another account, followed by a consent screen when the user has not granted access yet.
     * The outcome is passed to [onSuccess], [onDenied] and [onFailure].
     *
     * The token passed to [onSuccess] has [CloudAccessToken.account] set, which is needed to
     * refresh it. Persist it together with the token.
     */
    public fun authenticate() {
        Identity.getAuthorizationClient(activity)
            .authorize(buildGoogleDriveAuthorizationRequest(selectAccount = true))
            .addOnSuccessListener { result -> handle(result.toModel()) }
            .addOnFailureListener {
                logger.log("Google Drive authentication failed: $it")
                onFailure(CloudBridgeException.Unknown(it))
            }
    }

    private fun handle(authorization: GoogleAuthorization) {
        when (authorization) {
            is GoogleAuthorization.Authorized -> activity.lifecycleScope.launch {
                val token = try {
                    val account = fetchGoogleAccount(authorization.token.accessToken)
                        ?: throw CloudBridgeException.Unknown(
                            IllegalStateException("Google account has no email address")
                        )
                    authorization.token.copy(account = account)
                } catch (e: CloudBridgeException) {
                    logger.log("Failed to handle Google Drive authorization: $e")
                    onFailure(e)
                    return@launch
                }
                onSuccess(token)
            }
            is GoogleAuthorization.ConsentRequired -> consentLauncher.launch(
                IntentSenderRequest.Builder(authorization.pendingIntent.intentSender).build()
            )
            GoogleAuthorization.Denied -> onDenied()
        }
    }

    /**
     * Handles a flow that didn't finish with [Activity.RESULT_OK], either because of user
     * dismissing it, or because of an error.
     */
    private fun onNotOkResult(intent: Intent?) {
        if (intent == null) {
            onDenied()
            return
        }
        try {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent)
            onDenied()
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) {
                onDenied()
            } else {
                logger.log("Google Drive not OK: $e")
                onFailure(CloudBridgeException.Unknown(e))
            }
        }
    }

    /**
     * Parses the [intent] returned to the activity after the authorization consent screen and
     * returns the resulting [GoogleAuthorization].
     *
     * @throws CloudBridgeException when the authorization result could not be read.
     */
    private fun completeAuthentication(
        intent: Intent?
    ): GoogleAuthorization {
        val result = try {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent)
        } catch (e: ApiException) {
            throw CloudBridgeException.Unknown(e)
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

/**
 * @param account The account to authorize, or `null` to let Google Identity Services pick one.
 * @param selectAccount `true` to always show the account picker.
 */
internal fun buildGoogleDriveAuthorizationRequest(
    account: Account? = null,
    selectAccount: Boolean = false
): AuthorizationRequest {
    return AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .apply {
            if (account != null) {
                setAccount(account)
            }
            if (selectAccount) {
                setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            }
        }
        .build()
}

/**
 * Looks up the email address of the Google account that [accessToken] belongs to.
 *
 * @throws CloudBridgeException
 */
internal suspend fun fetchGoogleAccount(accessToken: String): String? {
    return GoogleDriveService(
        clientInfo = ClientInfo(clientId = ""),
        token = CloudAccessToken(accessToken = accessToken),
        tokenRefresher = null
    ).getUserInfo().emailAddress
}

internal const val GOOGLE_ACCOUNT_TYPE = "com.google"

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"