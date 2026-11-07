package nl.jacobras.cloudbridge.auth

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Represents an access token.
 *
 * @property accessToken The access token.
 * @property refreshToken The refresh token, to get a fresh [accessToken].
 * @property expiresAt The moment [accessToken] expires, or `null` if unknown.
 * @property account The account the token belongs to. Only needed for Google Drive on Android.
 */
public data class CloudAccessToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Instant? = null,
    val account: String? = null
)

/**
 * @return `true` if this token expires within [PREEMPTIVE_REFRESH_PERIOD] from [now] (or already has).
 */
internal fun CloudAccessToken.isExpiringSoon(now: Instant): Boolean {
    val expiresAt = expiresAt ?: return false
    return now >= expiresAt - PREEMPTIVE_REFRESH_PERIOD
}

/**
 * How long before [CloudAccessToken.expiresAt] the token gets refreshed.
 */
internal val PREEMPTIVE_REFRESH_PERIOD = 1.minutes