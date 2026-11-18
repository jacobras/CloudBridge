package nl.jacobras.cloudbridge.ratelimit

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import nl.jacobras.cloudbridge.CloudBridgeException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Network call rate limiter, to prevent getting rate limited by the cloud services.
 *
 * @param maxConcurrent Maximum number of concurrent network requests.
 * @param maxConcurrentWrites Maximum number of concurrent write requests. These also count towards
 * [maxConcurrent].
 * @param maxRatePerSecond Maximum number of network requests per second. Requests are spaced evenly.
 * @param maxRetries Maximum number of retries of a single request that gets rate-limited.
 * @param maxRetryDelay Max delay to wait for before rethrowing the [CloudBridgeException.RateLimitException].
 */
internal class RateLimiter internal constructor(
    val maxConcurrent: Int,
    val maxConcurrentWrites: Int = maxConcurrent,
    val maxRatePerSecond: Double,
    val maxRetries: Int = 3,
    val maxRetryDelay: Duration = 30.seconds,
    timeSource: TimeSource = TimeSource.Monotonic
) {

    init {
        require(maxConcurrent > 0) { "maxConcurrent must be positive" }
        require(maxConcurrentWrites > 0) { "maxConcurrentWrites must be positive" }
        require(maxRatePerSecond > 0.0) { "maxRatePerSecond must be positive" }
        require(maxRetries >= 0) { "maxRetries can't be negative" }
        require(!maxRetryDelay.isNegative()) { "maxRetryDelay can't be negative" }
    }

    private val semaphore = Semaphore(maxConcurrent)
    private val writeSemaphore = Semaphore(maxConcurrentWrites)
    private val mutex = Mutex()
    private val start = timeSource.markNow()
    private val interval = if (maxRatePerSecond == Double.POSITIVE_INFINITY) {
        Duration.ZERO
    } else {
        (1.0 / maxRatePerSecond).seconds
    }
    private var nextAllowed = Duration.ZERO

    /**
     * Waits for a free slot and the next moment a request is allowed to start.
     *
     * @param isWrite Whether the request writes, so it also needs a write slot. The write
     * slot is acquired first, so queued writes don't block reads.
     */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun acquire(isWrite: Boolean = false): Permit {
        val writeSlot = if (isWrite) {
            writeSemaphore
        } else {
            null
        }
        writeSlot?.acquire()
        try {
            semaphore.acquire()
        } catch (e: Throwable) {
            writeSlot?.release()
            throw e
        }
        val permit = Permit(semaphore, writeSlot)
        try {
            awaitStart()
        } catch (e: Throwable) {
            permit.release()
            throw e
        }
        return permit
    }

    /**
     * Holds back all requests on this limiter for at least [waitDuration].
     */
    internal suspend fun pause(waitDuration: Duration) {
        mutex.withLock {
            nextAllowed = maxOf(nextAllowed, start.elapsedNow() + waitDuration)
        }
    }

    private suspend fun awaitStart() {
        while (true) {
            val wait = mutex.withLock {
                val now = start.elapsedNow()
                if (now >= nextAllowed) {
                    nextAllowed = now + interval
                    Duration.ZERO
                } else {
                    nextAllowed - now
                }
            }
            if (wait == Duration.ZERO) {
                return
            }
            delay(wait)
        }
    }

    internal class Permit(
        private val semaphore: Semaphore,
        private val writeSemaphore: Semaphore? = null
    ) {
        private var released = false

        fun release() {
            if (!released) {
                semaphore.release()
                writeSemaphore?.release()
                released = true
            }
        }
    }
}