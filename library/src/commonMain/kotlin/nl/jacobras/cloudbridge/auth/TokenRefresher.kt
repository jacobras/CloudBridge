package nl.jacobras.cloudbridge.auth

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import nl.jacobras.cloudbridge.CloudBridgeException
import kotlin.time.Clock

/**
 * Refreshes an access token.
 */
internal interface TokenRefresher {

    /**
     * @param outdated The token to replace.
     * @param endpoint The token endpoint of the service.
     * @param clock Clock to calculate when the new token expires.
     * @return a new token, or `null` if [outdated] can't be refreshed.
     * @throws CloudBridgeException.NotAuthenticatedException if the user needs to
     * re-authenticate.
     */
    suspend fun refresh(
        outdated: CloudAccessToken,
        endpoint: TokenRefreshEndpoint,
        clock: Clock
    ): CloudAccessToken?
}

internal class OAuthTokenRefresher(private val clientInfo: ClientInfo) : TokenRefresher {

    override suspend fun refresh(
        outdated: CloudAccessToken,
        endpoint: TokenRefreshEndpoint,
        clock: Clock
    ): CloudAccessToken? {
        val refreshToken = outdated.refreshToken ?: return null
        val now = clock.now()
        val response = try {
            endpoint.refreshToken(
                refreshToken = refreshToken,
                clientId = clientInfo.clientId,
                clientSecret = clientInfo.googleDriveDesktopSecret?.takeIf { it.isNotEmpty() }
            )
        } catch (e: ResponseException) {
            throw when (e.response.status) {
                // Refresh token revoked or expired: user needs to re-authenticate.
                HttpStatusCode.BadRequest,
                HttpStatusCode.Unauthorized -> CloudBridgeException.NotAuthenticatedException()
                else -> CloudBridgeException.Unknown(e)
            }
        }

        return response.toCloudAccessToken(now).let {
            // Not all services return a new refresh token. If not, the old one remains valid.
            if (it.refreshToken == null) {
                it.copy(refreshToken = refreshToken)
            } else {
                it
            }
        }
    }
}