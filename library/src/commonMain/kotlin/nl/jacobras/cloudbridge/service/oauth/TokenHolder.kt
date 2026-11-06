package nl.jacobras.cloudbridge.service.oauth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import nl.jacobras.cloudbridge.CloudBridge.logger
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.PREEMPTIVE_REFRESH_PERIOD
import nl.jacobras.cloudbridge.auth.TokenRefreshEndpoint
import nl.jacobras.cloudbridge.auth.TokenRefresher
import nl.jacobras.cloudbridge.auth.isExpiringSoon
import kotlin.time.Clock

/**
 * Holds the token of an [OAuthCloudService] and refreshes it using [tokenRefresher].
 *
 * @param startToken The initial token.
 * @param tokenRefresher Gets a new token, or `null` if refreshing is not supported.
 * @param onTokenRefreshed Callback to persist the new token after a refresh.
 * @param endpoint The token endpoint of the service.
 * @param clock Clock, used to turn [expires in x seconds] into an exact instant.
 */
internal class TokenHolder(
    startToken: CloudAccessToken?,
    private val tokenRefresher: TokenRefresher?,
    private val onTokenRefreshed: ((CloudAccessToken) -> Unit)?,
    private val endpoint: TokenRefreshEndpoint,
    private val clock: Clock
) {
    private val mutex = Mutex()

    /**
     * The token that's currently used for requests.
     */
    var token: CloudAccessToken? = startToken

    /**
     * @return `true` if [token] expires within the [PREEMPTIVE_REFRESH_PERIOD].
     */
    fun isExpiringSoon(token: CloudAccessToken): Boolean = token.isExpiringSoon(clock.now())

    /**
     * Refreshes the token.
     *
     * @return `true` if a new token is available (refreshed by this or a concurrent call),
     * `false` if it can't be refreshed.
     * @throws CloudServiceException if refreshing failed.
     */
    suspend fun refresh(outdated: CloudAccessToken): Boolean {
        val tokenRefresher = tokenRefresher ?: return false

        return mutex.withLock {
            val current = token ?: return@withLock false
            if (current != outdated) {
                // Refreshed (or replaced) by someone else in the meantime.
                return@withLock true
            }

            val newToken = mapErrors {
                tokenRefresher.refresh(current, endpoint, clock)
            } ?: return@withLock false
            logger.log("Refreshed access token")
            token = newToken
            onTokenRefreshed?.invoke(newToken)
            true
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private inline fun <T> mapErrors(block: () -> T): T {
        return try {
            block()
        } catch (e: CloudServiceException) {
            logger.log("Failed to refresh access token: $e")
            throw e
        } catch (e: IOException) {
            logger.log("Failed to refresh access token: $e")
            throw CloudServiceException.ConnectionException(e)
        } catch (e: CancellationException) {
            logger.log("Failed to refresh access token: $e")
            throw e
        } catch (e: Throwable) {
            logger.log("Failed to refresh access token: $e")
            throw CloudServiceException.Unknown(e)
        }
    }
}