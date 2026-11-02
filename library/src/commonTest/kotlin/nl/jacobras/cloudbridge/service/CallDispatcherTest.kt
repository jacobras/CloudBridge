package nl.jacobras.cloudbridge.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CallDispatcherTest {

    @Test
    fun `succeed after rate limiting`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 3))
        var attempts = 0

        val result = dispatcher.tryCall("item") {
            if (attempts++ < 2) throw CloudServiceException.RateLimitException(null)
            "ok"
        }

        assertThat(result).isEqualTo("ok")
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun `throw after reaching maxRetries`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 3))
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudServiceException.RateLimitException(null)
            }
        }.isInstanceOf<CloudServiceException.RateLimitException>()
        assertThat(attempts).isEqualTo(4)
    }

    @Test
    fun `do not retry when maxRetries is 0`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 0))
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudServiceException.RateLimitException(null)
            }
        }.isInstanceOf<CloudServiceException.RateLimitException>()
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `do not retry without a rate limiter set`() = runTest {
        val dispatcher = dispatcher(null)
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudServiceException.RateLimitException(null)
            }
        }.isInstanceOf<CloudServiceException.RateLimitException>()
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `do not retry when not retryable`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 3))
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item", retryable = false) {
                attempts++
                throw CloudServiceException.RateLimitException(null)
            }
        }.isInstanceOf<CloudServiceException.RateLimitException>()
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `back off exponentially`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 3))
        val attemptTimes = mutableListOf<Long>()

        assertFailure {
            dispatcher.tryCall("item") {
                attemptTimes += currentTime
                throw CloudServiceException.RateLimitException(null)
            }
        }
        assertThat(attemptTimes).containsExactly(0L, 1_000L, 3_000L, 7_000L)
    }

    @Test
    fun `wait until retryAfter when longer than backoff`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 1))
        val attemptTimes = mutableListOf<Long>()

        dispatcher.tryCall("item") {
            attemptTimes += currentTime
            if (attemptTimes.size == 1) throw CloudServiceException.RateLimitException(10.seconds)
        }

        assertThat(attemptTimes).containsExactly(0L, 10_000L)
    }

    @Test
    fun `no retry when retryAfter exceeds maxRetryDelay`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 3, maxRetryDelay = 1.minutes))
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudServiceException.RateLimitException(2.minutes)
            }
        }.isInstanceOf<CloudServiceException.RateLimitException>()
        assertThat(attempts).isEqualTo(1)
        assertThat(currentTime).isEqualTo(0L)
    }

    @Test
    fun `release permits even on failure`() = runTest {
        val dispatcher = dispatcher(limiter(maxConcurrent = 1, maxRetries = 1))

        repeat(5) {
            dispatcher.tryCall("item") { }
            assertFailure {
                dispatcher.tryCall("item") { throw CloudServiceException.RateLimitException(null) }
            }
            assertFailure {
                dispatcher.tryCall("item") { error("Boom") }
            }.isInstanceOf<CloudServiceException.Unknown>()
        }
    }

    private fun TestScope.limiter(
        maxConcurrent: Int = 5,
        maxRetries: Int,
        maxRetryDelay: Duration = 1.minutes
    ) = RateLimiter(
        maxConcurrent,
        Double.POSITIVE_INFINITY,
        maxRetries,
        maxRetryDelay,
        testTimeSource
    )

    private fun dispatcher(rateLimiter: RateLimiter?) = CallDispatcher(
        rateLimiter = rateLimiter,
        authCheck = { true },
        responseErrorMapper = { CloudServiceException.Unknown(this) }
    )
}
