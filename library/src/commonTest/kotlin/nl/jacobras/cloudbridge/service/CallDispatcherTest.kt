package nl.jacobras.cloudbridge.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.OAuthTokenRefresher
import nl.jacobras.cloudbridge.auth.TokenResponse
import nl.jacobras.cloudbridge.ratelimit.RateLimiter
import nl.jacobras.cloudbridge.service.oauth.TokenHolder
import nl.jacobras.cloudbridge.util.testClientInfo
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
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
            if (attempts++ == 0) throw CloudServiceException.NotAuthenticatedException()
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
                throw CloudServiceException.NotAuthenticatedException()
            }
        }.isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(attempts).isEqualTo(2)
    }

    @Test
    fun `no retry on authentication failure when token can't be refreshed`() = runTest {
        val dispatcher = dispatcher(null, FakeTokenHolder(canRefresh = false))
        var attempts = 0

        assertFailure {
            dispatcher.tryCall("item") {
                attempts++
                throw CloudServiceException.NotAuthenticatedException()
            }
        }.isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(attempts).isEqualTo(1)
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
        tokenHolder: FakeTokenHolder? = null
    ) = CallDispatcher(
        rateLimiter = rateLimiter,
        authCheck = { true },
        tokenHolder = tokenHolder?.holder,
        responseErrorMapper = { CloudServiceException.Unknown(this) }
    )
}