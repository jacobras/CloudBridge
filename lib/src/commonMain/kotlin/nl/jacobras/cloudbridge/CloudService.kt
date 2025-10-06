package nl.jacobras.cloudbridge

public interface CloudService {

    /**
     * Returns `true` if the account is authenticated.
     */
    public fun isAuthenticated(): Boolean

    /**
     * Returns a [CloudAuthenticator] that can be used to authorize a user.
     *
     * @param redirectUri URI to redirect user to upon successful authorization.
     * Needs to be registered in the service's developer console.
     */
    public fun getAuthenticator(
        redirectUri: String
    ): CloudAuthenticator

    /**
     * Clears all tokens for this service.
     */
    public fun logout()

    /**
     * Lists all files.
     */
    public suspend fun listFiles(): List<String>
}