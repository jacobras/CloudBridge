package nl.jacobras.cloudbridge.util

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.fromHttpToGmtDate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * How long the server asked to wait before trying again, from its `Retry-After` header.
 *
 * @return The wait, or `null` when the header is missing or can't be parsed.
 */
internal fun HttpResponse.retryAfter(now: Instant = Clock.System.now()): Duration? {
    return parseRetryAfterHeader(headers[HttpHeaders.RetryAfter], now)
}

/**
 * Parses a `Retry-After` value, which is either a number of seconds or an HTTP date.
 */
internal fun parseRetryAfterHeader(value: String?, now: Instant): Duration? {
    val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    trimmed.toLongOrNull()?.let { return it.coerceAtLeast(0).seconds }

    val date = try {
        trimmed.fromHttpToGmtDate()
    } catch (_: Exception) {
        return null
    }
    val wait = (date.timestamp - now.toEpochMilliseconds()).milliseconds
    return wait.coerceAtLeast(Duration.ZERO)
}