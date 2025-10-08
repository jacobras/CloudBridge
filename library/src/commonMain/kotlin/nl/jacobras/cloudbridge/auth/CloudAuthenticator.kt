package nl.jacobras.cloudbridge.auth

import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Can be used to authorize a user.
 */
public sealed interface CloudAuthenticator {

    /**
     * Builds an authorization URI. Navigate the user to this URI to
     * start the authorization flow.
     */
    public fun buildUri(): String
}

/**
 * Authenticator using Implicit Flow.
 *
 * https://www.oauth.com/oauth2-servers/single-page-apps/implicit-flow/
 */
public interface ImplicitAuthenticator : CloudAuthenticator {

    /**
     * Stores the [token] for the cloud service.
     */
    public fun storeToken(token: String)
}

/**
 * Authenticator using PKCE flow.
 *
 * https://www.oauth.com/oauth2-servers/pkce/
 */
public abstract class PkceAuthenticator(protected val codeVerifier: String) : CloudAuthenticator {

    protected val codeChallenge: String = SecurityUtil.buildCodeChallenge(codeVerifier)

    /**
     * @throws CloudServiceException
     */
    public abstract suspend fun exchangeCodeForToken(code: String)
}