package nl.jacobras.cloudbridge.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
internal data class TokenResponse(

    @SerialName("access_token")
    val accessToken: String,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    @SerialName("expires_in")
    val expiresInSeconds: Int? = null
)

/**
 * @param now The moment the token was issued, to calculate [CloudAccessToken.expiresAt].
 */
internal fun TokenResponse.toCloudAccessToken(now: Instant = Clock.System.now()) = CloudAccessToken(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = expiresInSeconds?.let { now + it.seconds }
)