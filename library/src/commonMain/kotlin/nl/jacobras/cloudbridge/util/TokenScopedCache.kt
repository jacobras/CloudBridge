package nl.jacobras.cloudbridge.util

import nl.jacobras.cloudbridge.auth.CloudAccessToken

/**
 * Caches a single value per access token, to prevent this being returned for the wrong token.
 *
 * @param currentToken Should return the token that's currently in use.
 */
internal class TokenScopedCache<T : Any>(private val currentToken: () -> CloudAccessToken?) {
    private var value: Pair<CloudAccessToken?, T>? = null

    /**
     * Returns the cached value if it was fetched with the current token, or else [fetch]es it.
     */
    suspend fun getOrFetch(fetch: suspend () -> T): T {
        val token = currentToken()
        value?.let { (cachedToken, value) ->
            if (cachedToken == token) {
                // The cached value was fetched with the currently active token, so it's good to use.
                return value
            }
        }
        return fetch().also {
            if (currentToken() == token) {
                // Token still the same as at the start of this request, so good to be cached.
                value = token to it
            }
        }
    }
}