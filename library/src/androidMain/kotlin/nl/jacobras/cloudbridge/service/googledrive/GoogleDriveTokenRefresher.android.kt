package nl.jacobras.cloudbridge.service.googledrive

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.TokenRefresher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Drive on Android gets tokens from Google Identity Services.
 */
internal actual fun googleDriveTokenRefresher(clientInfo: ClientInfo): TokenRefresher? {
    return GoogleIdentityTokenRefresher()
}

internal suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
}