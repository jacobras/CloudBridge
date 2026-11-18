package nl.jacobras.cloudbridge.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.OAuthTokenRefresher
import nl.jacobras.cloudbridge.auth.TokenResponse
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import nl.jacobras.cloudbridge.service.oauth.TokenHolder
import nl.jacobras.cloudbridge.util.testClientInfo
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val now = Instant.parse("2026-01-01T12:00:00Z")

@OptIn(ExperimentalCoroutinesApi::class)
class CallDispatcherTest {

    @Test
    fun `succeed after rate limiting`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 3))
        var attempts = 0

        val result = dispatcher.tryCall("item") {
            if (attempts++ < 2) throw CloudBridgeException.RateLimitException(null)
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
                throw CloudBridgeException.RateLimitException(null)
            }
        }.isInstanceOf<CloudBridgeException.RateLimitException>()
        assertThat(attempts).isEqualTo(4)
    }

    @Test
    fun `do not retry when maxRetries is 0`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 0))
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudBridgeException.RateLimitException(null)
            }
        }.isInstanceOf<CloudBridgeException.RateLimitException>()
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `do not retry without a rate limiter set`() = runTest {
        val dispatcher = dispatcher(null)
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudBridgeException.RateLimitException(null)
            }
        }.isInstanceOf<CloudBridgeException.RateLimitException>()
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `back off exponentially`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 3))
        val attemptTimes = mutableListOf<Long>()

        assertFailure {
            dispatcher.tryCall("item") {
                attemptTimes += currentTime
                throw CloudBridgeException.RateLimitException(null)
            }
        }
        assertThat(attemptTimes).containsExactly(0L, 1_000L, 3_000L, 7_000L)
    }

    @Test
    fun `add up to 50 percent jitter to the back-off`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 3), random = Random(seed = 42))
        val attemptTimes = mutableListOf<Long>()

        assertFailure {
            dispatcher.tryCall("item") {
                attemptTimes += currentTime
                throw CloudBridgeException.RateLimitException(null)
            }
        }
        val delays = attemptTimes.zipWithNext { a, b -> b - a }
        assertThat(delays[0]).isBetween(1_000L, 1_500L)
        assertThat(delays[1]).isBetween(2_000L, 3_000L)
        assertThat(delays[2]).isBetween(4_000L, 6_000L)
    }

    @Test
    fun `writes run one at a time when maxConcurrentWrites is 1`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 0, maxConcurrentWrites = 1))
        var activeWrites = 0
        var maxActiveWrites = 0
        var activeTotal = 0
        var maxActiveTotal = 0

        suspend fun work(isWrite: Boolean) {
            dispatcher.tryCall("item", isWrite) {
                activeTotal++

                if (isWrite) {
                    activeWrites++
                }

                maxActiveTotal = maxOf(maxActiveTotal, activeTotal)
                maxActiveWrites = maxOf(maxActiveWrites, activeWrites)
                delay(100.milliseconds)

                if (isWrite) {
                    activeWrites--
                }

                activeTotal--
            }
        }
        repeat(3) { launch { work(isWrite = true) } }
        repeat(3) { launch { work(isWrite = false) } }
        testScheduler.advanceUntilIdle()

        assertThat(maxActiveWrites).isEqualTo(1)
        assertThat(maxActiveTotal).isEqualTo(4)
    }

    @Test
    fun `wait until retryAfter when longer than backoff`() = runTest {
        val dispatcher = dispatcher(limiter(maxRetries = 1))
        val attemptTimes = mutableListOf<Long>()

        dispatcher.tryCall("item") {
            attemptTimes += currentTime
            if (attemptTimes.size == 1) throw CloudBridgeException.RateLimitException(10.seconds)
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
                throw CloudBridgeException.RateLimitException(2.minutes)
            }
        }.isInstanceOf<CloudBridgeException.RateLimitException>()
        assertThat(attempts).isEqualTo(1)
        assertThat(currentTime).isEqualTo(0L)
    }

    @Test
    fun `release permits even on failure`() = runTest {
        val dispatcher = dispatcher(limiter(maxConcurrent = 1, maxRetries = 1))

        repeat(5) {
            dispatcher.tryCall("item") { }
            assertFailure {
                dispatcher.tryCall("item") { throw CloudBridgeException.RateLimitException(null) }
            }
            assertFailure {
                dispatcher.tryCall("item") { error("Boom") }
            }.isInstanceOf<CloudBridgeException.Unknown>()
        }
    }

    @Test
    fun `refresh before the call when token expires within 1 minute`() = runTest {
        val refresher = FakeTokenHolder(canRefresh = true, expiresAt = now + 59.seconds)
        val dispatcher = dispatcher(null, refresher)

        dispatcher.tryCall("item") { }

        assertThat(refresher.refreshed).containsExactly("token1")
    }

    @Test
    fun `no refresh before the call when token expires in more than 1 minute`() = runTest {
        val refresher = FakeTokenHolder(canRefresh = true, expiresAt = now + 61.seconds)
        val dispatcher = dispatcher(null, refresher)

        dispatcher.tryCall("item") { }

        assertThat(refresher.refreshed).isEmpty()
    }

    @Test
    fun `retry once after refreshing on authentication failure`() = runTest {
        val refresher = FakeTokenHolder(canRefresh = true)
        val dispatcher = dispatcher(null, refresher)
        var attempts = 0

        val result = dispatcher.tryCall("item") {
            if (attempts++ == 0) throw CloudBridgeException.NotAuthenticatedException()
            "ok"
        }

        assertThat(result).isEqualTo("ok")
        assertThat(attempts).isEqualTo(2)
        assertThat(refresher.refreshed).containsExactly("token1")
    }

    @Test
    fun `throw when authentication fails again after refreshing`() = runTest {
        val dispatcher = dispatcher(null, FakeTokenHolder(canRefresh = true))
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudBridgeException.NotAuthenticatedException()
            }
        }.isInstanceOf<CloudBridgeException.NotAuthenticatedException>()
        assertThat(attempts).isEqualTo(2)
    }

    @Test
    fun `no retry on authentication failure when token can't be refreshed`() = runTest {
        val dispatcher = dispatcher(null, FakeTokenHolder(canRefresh = false))
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudBridgeException.NotAuthenticatedException()
            }
        }.isInstanceOf<CloudBridgeException.NotAuthenticatedException>()
        assertThat(attempts).isEqualTo(1)
    }

    private fun TestScope.limiter(
        maxConcurrent: Int = 5,
        maxRetries: Int,
        maxRetryDelay: Duration = 1.minutes,
        maxConcurrentWrites: Int = maxConcurrent
    ) = RateLimiter(
        maxConcurrent = maxConcurrent,
        maxConcurrentWrites = maxConcurrentWrites,
        maxRatePerSecond = Double.POSITIVE_INFINITY,
        maxRetries = maxRetries,
        maxRetryDelay = maxRetryDelay,
        timeSource = testTimeSource
    )

    /**
     * [TokenHolder] that records which access tokens it refreshed.
     *
     * @param canRefresh Disable token refresh.
     */
    private class FakeTokenHolder(canRefresh: Boolean, expiresAt: Instant? = null) {
        val refreshed = mutableListOf<String>()
        val holder: TokenHolder = TokenHolder(
            startToken = CloudAccessToken(
                accessToken = "token1",
                refreshToken = if (canRefresh) {
                    "refresh"
                } else {
                    null
                },
                expiresAt = expiresAt
            ),
            tokenRefresher = OAuthTokenRefresher(testClientInfo),
            onTokenRefreshed = null,
            endpoint = { _, _, _ ->
                refreshed += checkNotNull(holder.token).accessToken
                TokenResponse(accessToken = "token${refreshed.size + 1}")
            },
            clock = object : Clock {
                override fun now() = now
            }
        )
    }

    private fun dispatcher(
        rateLimiter: RateLimiter?,
        tokenHolder: FakeTokenHolder? = null,
        random: Random = AlwaysZeroRandom
    ) = CallDispatcher(
        rateLimiter = rateLimiter,
        authCheck = { true },
        tokenHolder = tokenHolder?.holder,
        random = random,
        responseErrorMapper = { CloudBridgeException.Unknown(this) }
    )
}

private object AlwaysZeroRandom : Random() {
    override fun nextBits(bitCount: Int): Int = 0
}