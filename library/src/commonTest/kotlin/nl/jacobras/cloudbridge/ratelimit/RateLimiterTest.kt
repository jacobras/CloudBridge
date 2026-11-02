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
        val limiter = RateLimiter(2, Double.POSITIVE_INFINITY, 0, 1.minutes, testTimeSource)
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
        val limiter = RateLimiter(100, 5.0, 0, 1.minutes, testTimeSource)
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
        val limiter = RateLimiter(100, 100.0, 0, 1.minutes, testTimeSource)

        limiter.pause(3.seconds)
        limiter.acquire().release()

        assertThat(currentTime).isEqualTo(3000L)
    }

    @Test
    fun `releasing a permit twice only frees one slot`() = runTest {
        val limiter = RateLimiter(1, Double.POSITIVE_INFINITY, 0, 1.minutes, testTimeSource)
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
        val limiter = RateLimiter(1, 1.0, 0, 1.minutes, testTimeSource)
        limiter.acquire().release()

        val waiting = launch { limiter.acquire() }
        runCurrent()
        waiting.cancel()
        runCurrent()

        limiter.acquire().release()
        assertThat(currentTime).isEqualTo(1000L)
    }

    @Test
    fun `maxConcurrent cannot be negative`() {
        assertFailure {
            RateLimiter(-1, 1.0)
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `maxRatePerSecond must be positive`() {
        assertFailure {
            RateLimiter(1, 0.0)
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `maxRetries must be positive`() {
        assertFailure {
            RateLimiter(1, 0.0, maxRetries = -1)
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `maxRetryDelay must be positive`() {
        assertFailure {
            RateLimiter(1, 0.0, maxRetryDelay = (-1).seconds)
        }.isInstanceOf<IllegalArgumentException>()
    }
}