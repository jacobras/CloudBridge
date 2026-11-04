package nl.jacobras.cloudbridge.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RetryAfterHeaderParserTest {

    private val now = Instant.parse("2026-11-02T12:00:00Z")

    @Test
    fun `missing or blank value is null`() {
        assertThat(parseRetryAfterHeader(null, now)).isNull()
        assertThat(parseRetryAfterHeader("  ", now)).isNull()
    }

    @Test
    fun `seconds are parsed`() {
        assertThat(parseRetryAfterHeader("120", now)).isEqualTo(120.seconds)
        assertThat(parseRetryAfterHeader(" 5 ", now)).isEqualTo(5.seconds)
    }

    @Test
    fun `negative seconds become zero`() {
        assertThat(parseRetryAfterHeader("-3", now)).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `HTTP date is parsed relative to now`() {
        assertThat(parseRetryAfterHeader("Mon, 02 Nov 2026 12:00:30 GMT", now)).isEqualTo(30.seconds)
    }

    @Test
    fun `HTTP date in the past becomes zero`() {
        assertThat(parseRetryAfterHeader("Mon, 02 Nov 2026 11:59:00 GMT", now)).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `unparseable value is null`() {
        assertThat(parseRetryAfterHeader("invalid", now)).isNull()
    }
}