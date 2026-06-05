package nl.jacobras.cloudbridge.auth

import kotlin.time.Duration

/**
 * Represents an access token.
 *
 * @property accessToken The access token.
 * @property refreshToken The refresh token, to get a fresh [accessToken].
 * @property expiresIn The duration until [accessToken] expires.
 */
public data class AccessToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Duration? = null
)