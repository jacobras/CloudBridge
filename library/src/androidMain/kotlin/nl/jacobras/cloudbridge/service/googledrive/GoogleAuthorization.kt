package nl.jacobras.cloudbridge.service.googledrive

import android.app.PendingIntent
import nl.jacobras.cloudbridge.auth.CloudAccessToken

/**
 * Outcome of authorization through Google Identity Services.
 */
internal sealed interface GoogleAuthorization {
    data class Authorized(
        val token: CloudAccessToken
    ) : GoogleAuthorization

    data class ConsentRequired(
        val pendingIntent: PendingIntent
    ) : GoogleAuthorization

    data object Denied : GoogleAuthorization
}