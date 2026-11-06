package nl.jacobras.cloudbridge.auth

/**
 * Calls a service's OAuth token endpoint to get a new token.
 */
internal fun interface TokenRefreshEndpoint {
    suspend fun refreshToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String?
    ): TokenResponse
}