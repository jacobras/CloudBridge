package nl.jacobras.cloudbridge

import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Can be used to authorize a user.
 */
public abstract class CloudAuthenticator(
    public val codeVerifier: String
) {
    protected val codeChallenge: String = SecurityUtil.buildCodeChallenge(codeVerifier)

    /**
     * Builds an authorization URI. Navigate the user to this URI to
     * start the authorization flow.
     */
    public abstract fun buildUri(): String

    public abstract suspend fun exchangeCodeForToken(code: String)
}