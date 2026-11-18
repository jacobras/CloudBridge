package nl.jacobras.cloudbridge.service.googledrive

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlinx.coroutines.test.runTest
import nl.jacobras.cloudbridge.CloudBridgeException
import kotlin.test.Test

class GoogleAccountLookupTest {

    private val cleared = mutableListOf<String>()
    private val rejected = CloudBridgeException.NotAuthenticatedException(
        IllegalStateException("401 Unauthorized")
    )

    @Test
    fun `valid token returns the account`() = runTest {
        val lookup = lookUp(canRetry = true) { "user@example.com" }

        assertThat(lookup).isEqualTo(GoogleAccountLookup.Found("user@example.com"))
        assertThat(cleared).isEmpty()
    }

    @Test
    fun `rejected token is cleared so authorization can be retried`() = runTest {
        val lookup = lookUp(canRetry = true) { throw rejected }

        assertThat(lookup).isEqualTo(GoogleAccountLookup.StaleToken)
        assertThat(cleared).containsExactly("token")
    }

    @Test
    fun `rejected token after retry fails with the cause intact`() = runTest {
        assertFailure { lookUp(canRetry = false) { throw rejected } }
            .isSameInstanceAs(rejected)
        assertThat(cleared).isEmpty()
    }

    @Test
    fun `failure to clear the token fails with the rejection`() = runTest {
        assertFailure {
            lookUpGoogleAccount(
                accessToken = "token",
                canRetry = true,
                fetchAccount = { throw rejected },
                clearToken = { error("Play services unavailable") }
            )
        }.isSameInstanceAs(rejected)
    }

    @Test
    fun `other failures are not retried`() = runTest {
        val failure = CloudBridgeException.RateLimitException(retryAfter = null)

        assertFailure { lookUp(canRetry = true) { throw failure } }.isSameInstanceAs(failure)
        assertThat(cleared).isEmpty()
    }

    @Test
    fun `account without email fails`() = runTest {
        assertFailure { lookUp(canRetry = true) { null } }
            .isInstanceOf<CloudBridgeException.Unknown>()
    }

    private suspend fun lookUp(
        canRetry: Boolean,
        fetchAccount: suspend (String) -> String?
    ) = lookUpGoogleAccount(
        accessToken = "token",
        canRetry = canRetry,
        fetchAccount = fetchAccount,
        clearToken = { cleared += it }
    )
}
