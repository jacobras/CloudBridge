package nl.jacobras.cloudbridge.service

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import nl.jacobras.cloudbridge.CloudBridge.logger
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import nl.jacobras.cloudbridge.service.oauth.TokenHolder
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class CallDispatcher(
    private val rateLimiter: RateLimiter?,
    private val authCheck: () -> Boolean,
    private val tokenHolder: TokenHolder? = null,
    private val random: Random = Random.Default,
    private val responseErrorMapper: ResponseException.(itemId: String) -> CloudBridgeException
) {

    /**
     * Executes [call], up to [RateLimiter.maxRetries] times during rate-limiting.
     *
     * Refreshes the token before the call if it's about to expire, and retries once
     * after refreshing.
     *
     * @param isWrite Whether [call] writes, so it's limited by [RateLimiter.maxConcurrentWrites].
     * @param call The call to execute, must be safe to execute multiple times.
     */
    suspend fun <T> tryCall(
        itemId: String,
        isWrite: Boolean = false,
        call: suspend () -> T
    ): T {
        if (!authCheck()) {
            throw CloudBridgeException.NotAuthenticatedException()
        }

        if (tokenHolder != null) {
            val token = tokenHolder.token
            if (token != null && tokenHolder.isExpiringSoon(token)) {
                tokenHolder.refresh(token)
            }
        }

        var attempt = 0
        var refreshedAfterUnauthorized = false
        while (true) {
            val permit = rateLimiter?.acquire(isWrite)
            val usedToken = tokenHolder?.token
            try {
                return mapError(itemId, call)
            } catch (e: CloudBridgeException.NotAuthenticatedException) {
                if (tokenHolder == null || usedToken == null || refreshedAfterUnauthorized) {
                    throw e
                }

                permit?.release()
                refreshedAfterUnauthorized = true

                if (!tokenHolder.refresh(usedToken)) {
                    logger.log("Failed to refresh token")
                    throw e
                }

                logger.log("Token refreshed after authentication failure on $itemId, retrying call")
            } catch (e: CloudBridgeException.RateLimitException) {
                val retryDelay = calculateRetryDelay(e, attempt) ?: throw e
                permit?.release()
                attempt++
                logger.log("Rate limited on $itemId, retry $attempt in $retryDelay")
                rateLimiter?.pause(retryDelay)
            } finally {
                permit?.release()
            }
        }
    }

    /**
     * Calculates how long to wait after [rateLimitException]. Does exponential back-off with
     * up to [MAX_JITTER] jitter, so concurrent retries don't all fire at the same moment.
     *
     * @return the delay duration or `null` if not needed.
     */
    private fun calculateRetryDelay(
        rateLimitException: CloudBridgeException.RateLimitException,
        attempt: Int
    ): Duration? {
        val limiter = rateLimiter ?: return null
        if (attempt >= limiter.maxRetries) return null

        val backoff = (2.0.pow(attempt) * (1 + random.nextDouble() * MAX_JITTER)).seconds
        val retryDelay = maxOf(rateLimitException.retryAfter ?: Duration.ZERO, backoff)
        return retryDelay.takeIf { it <= limiter.maxRetryDelay }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> mapError(itemId: String, call: suspend () -> T): T {
        return try {
            call()
        } catch (e: ResponseException) {
            throw e.responseErrorMapper(itemId)
        } catch (e: IOException) {
            throw CloudBridgeException.ConnectionException(e)
        } catch (e: CloudBridgeException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw CloudBridgeException.Unknown(e)
        }
    }
}

/**
 * Maximum fraction of the back-off jitter to randomly add (to prevent everything firing again
 * at the same time).
 */
private const val MAX_JITTER = 0.5