package nl.jacobras.cloudbridge.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class TokenResponse(

    @SerialName("access_token")
    val accessToken: String,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    @SerialName("expires_in")
    val expiresInSeconds: Int? = null
)

internal fun TokenResponse.toCloudAccessToken() = CloudAccessToken(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresIn = expiresInSeconds?.seconds
)