package nl.jacobras.cloudbridge.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import kotlin.test.Test

class TokenScopedCacheTest {

    @Test
    fun `value is reused while the token stays the same`() = runTest {
        val cache = TokenScopedCache<String> { CloudAccessToken("first") }
        var fetches = 0

        cache.getOrFetch { "value${++fetches}" }
        val value = cache.getOrFetch { "value${++fetches}" }

        assertThat(value).isEqualTo("value1")
        assertThat(fetches).isEqualTo(1)
    }

    @Test
    fun `value is fetched again after the token changed`() = runTest {
        var token = CloudAccessToken("first")
        val cache = TokenScopedCache<String> { token }

        cache.getOrFetch { "first" }
        token = CloudAccessToken("second")
        val value = cache.getOrFetch { "second" }

        assertThat(value).isEqualTo("second")
    }

    @Test
    fun `value is not cached when the token changed while fetching`() = runTest {
        var token = CloudAccessToken("first")
        val cache = TokenScopedCache<String> { token }

        cache.getOrFetch {
            token = CloudAccessToken("second")
            "first"
        }
        val value = cache.getOrFetch { "second" }

        assertThat(value).isEqualTo("second")
    }
}