package nl.jacobras.cloudbridge.service.googledrive

import kotlinx.coroutines.CancellationException
import nl.jacobras.cloudbridge.CloudBridge.logger
import nl.jacobras.cloudbridge.CloudBridgeException

internal sealed interface GoogleAccountLookup {

    /**
     * @param email The email address of the account the token belongs to.
     */
    data class Found(val email: String) : GoogleAccountLookup

    /**
     * The token was rejected and has been cleared from the token cache, so authorization should
     * be requested again to get a fresh token.
     */
    data object StaleToken : GoogleAccountLookup
}

/**
 * Looks up the account of a freshly obtained [accessToken], to finish linking Google Drive.
 *
 * Google Identity Services on Android caches access tokens and can return one that Google no
 * longer accepts (for example after the grant was revoked or replaced by another client with the
 * same OAuth client ID). If [canRetry], such a token is cleared through [clearToken], so the next
 * authorization returns a new one.
 *
 * @param fetchAccount Gets the email address of the account [accessToken] belongs to.
 * @param clearToken Removes the token from the token cache.
 * @throws CloudBridgeException if the account can't be looked up.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun lookUpGoogleAccount(
    accessToken: String,
    canRetry: Boolean,
    fetchAccount: suspend (accessToken: String) -> String?,
    clearToken: suspend (accessToken: String) -> Unit
): GoogleAccountLookup {
    val email = try {
        fetchAccount(accessToken)
    } catch (e: CloudBridgeException.NotAuthenticatedException) {
        if (!canRetry) {
            logger.warn("Google rejected the access token again after clearing it", e)
            throw e
        }
        logger.warn("Google rejected the new access token, clearing it and retrying", e)
        try {
            clearToken(accessToken)
        } catch (e: CancellationException) {
            throw e
        } catch (clearException: Throwable) {
            logger.warn("Failed to clear the rejected access token", clearException)
            throw e
        }
        return GoogleAccountLookup.StaleToken
    }
    if (email == null) {
        throw CloudBridgeException.Unknown(IllegalStateException("Google account has no email address"))
    }
    return GoogleAccountLookup.Found(email)
}