package nl.jacobras.cloudbridge.ratelimit

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {

    @Test
    fun `concurrency never exceeds maxConcurrent`() = runTest {
        val limiter = RateLimiter(
            maxConcurrent = 2,
            maxRatePerSecond = Double.POSITIVE_INFINITY,
            maxRetries = 0,
            maxRetryDelay = 1.minutes,
            timeSource = testTimeSource
        )
        var active = 0
        var maxActive = 0

        repeat(10) {
            launch {
                val permit = limiter.acquire()
                active++
                maxActive = maxOf(maxActive, active)
                delay(100.milliseconds)
                active--
                permit.release()
            }
        }
        testScheduler.advanceUntilIdle()

        assertThat(maxActive).isEqualTo(2)
    }

    @Test
    fun `requests are spaced evenly by maxRatePerSecond`() = runTest {
        val limiter = RateLimiter(
            maxConcurrent = 100,
            maxRatePerSecond = 5.0,
            maxRetries = 0,
            maxRetryDelay = 1.minutes,
            timeSource = testTimeSource
        )
        val startTimes = mutableListOf<Long>()

        repeat(3) {
            launch {
                limiter.acquire().release()
                startTimes += currentTime
            }
        }
        testScheduler.advanceUntilIdle()

        assertThat(startTimes).containsExactly(0L, 200L, 400L)
    }

    @Test
    fun `pause holds back all requests`() = runTest {
        val limiter = RateLimiter(
            maxConcurrent = 100,
            maxRatePerSecond = 100.0,
            maxRetries = 0,
            maxRetryDelay = 1.minutes,
            timeSource = testTimeSource
        )

        limiter.pause(3.seconds)
        limiter.acquire().release()

        assertThat(currentTime).isEqualTo(3000L)
    }

    @Test
    fun `releasing a permit twice only frees one slot`() = runTest {
        val limiter = RateLimiter(
            maxConcurrent = 1,
            maxRatePerSecond = Double.POSITIVE_INFINITY,
            maxRetries = 0,
            maxRetryDelay = 1.minutes,
            timeSource = testTimeSource
        )
        val permit = limiter.acquire()
        permit.release()
        permit.release()

        limiter.acquire()
        var acquiredSecond = false
        val job = launch {
            limiter.acquire()
            acquiredSecond = true
        }
        runCurrent()

        assertThat(acquiredSecond).isEqualTo(false)
        job.cancel()
    }

    @Test
    fun `cancelling while waiting for the rate frees the slot`() = runTest {
        val limiter = RateLimiter(
            maxConcurrent = 1,
            maxRatePerSecond = 1.0,
            maxRetries = 0,
            maxRetryDelay = 1.minutes,
            timeSource = testTimeSource
        )
        limiter.acquire().release()

        val waiting = launch { limiter.acquire() }
        runCurrent()
        waiting.cancel()
        runCurrent()

        limiter.acquire().release()
        assertThat(currentTime).isEqualTo(1000L)
    }

    @Test
    fun `writes wait for a write slot while reads go ahead`() = runTest {
        val limiter = RateLimiter(
            maxConcurrent = 5,
            maxConcurrentWrites = 1,
            maxRatePerSecond = Double.POSITIVE_INFINITY,
            maxRetries = 0,
            maxRetryDelay = 1.minutes,
            timeSource = testTimeSource
        )
        val firstWrite = limiter.acquire(isWrite = true)

        var acquiredSecondWrite = false
        val secondWrite = launch {
            limiter.acquire(isWrite = true)
            acquiredSecondWrite = true
        }
        runCurrent()
        assertThat(acquiredSecondWrite).isEqualTo(false)

        limiter.acquire().release()

        firstWrite.release()
        runCurrent()
        assertThat(acquiredSecondWrite).isEqualTo(true)
        secondWrite.cancel()
    }

    @Test
    fun `maxConcurrentWrites must be positive`() {
        assertFailure {
            RateLimiter(maxConcurrent = 1, maxConcurrentWrites = 0, maxRatePerSecond = 1.0)
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `maxConcurrent cannot be negative`() {
        assertFailure {
            RateLimiter(maxConcurrent = -1, maxRatePerSecond = 1.0)
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `maxRatePerSecond must be positive`() {
        assertFailure {
            RateLimiter(maxConcurrent = 1, maxRatePerSecond = 0.0)
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `maxRetries must be positive`() {
        assertFailure {
            RateLimiter(maxConcurrent = 1, maxRatePerSecond = 0.0, maxRetries = -1)
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `maxRetryDelay must be positive`() {
        assertFailure {
            RateLimiter(maxConcurrent = 1, maxRatePerSecond = 0.0, maxRetryDelay = (-1).seconds)
        }.isInstanceOf<IllegalArgumentException>()
    }
}