package nl.jacobras.cloudbridge.service

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import nl.jacobras.cloudbridge.CloudBridge.logger
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class CallDispatcher(
    private val rateLimiter: RateLimiter?,
    private val authCheck: () -> Boolean,
    private val responseErrorMapper: ResponseException.(itemId: String) -> CloudServiceException
) {

    /**
     * Executes [call], up to [RateLimiter.maxRetries] times during rate-limiting.
     *
     * @param call The call to execute, must be safe to execute multiple times.
     */
    suspend fun <T> tryCall(
        itemId: String,
        call: suspend () -> T
    ): T {
        if (!authCheck()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        var attempt = 0
        while (true) {
            val permit = rateLimiter?.acquire()
            try {
                return mapError(itemId, call)
            } catch (e: CloudServiceException.RateLimitException) {
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
     * Calculates how long to wait after [rateLimitException]. Does exponential back-off.
     *
     * @return the delay duration or `null` if not needed.
     */
    private fun calculateRetryDelay(
        rateLimitException: CloudServiceException.RateLimitException,
        attempt: Int
    ): Duration? {
        val limiter = rateLimiter ?: return null
        if (attempt >= limiter.maxRetries) return null

        val backoff = 2.0.pow(attempt).seconds
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
            throw CloudServiceException.ConnectionException(e)
        } catch (e: CloudServiceException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw CloudServiceException.Unknown(e)
        }
    }
}