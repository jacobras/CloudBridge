package nl.jacobras.cloudbridge.auth

import net.thauvin.erik.urlencoder.UrlEncoderUtil
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
public abstract class ImplicitAuthenticator(
    private val clientId: String,
    private val redirectUri: String
) : CloudAuthenticator {

    protected abstract val baseUrl: String
    protected abstract val scope: String

    public override fun buildUri(): String {
        val encodedRedirectUri = UrlEncoderUtil.encode(redirectUri)

        return buildString {
            append(baseUrl)
            append("?client_id=$clientId")
            append("&scope=$scope")
            append("&response_type=token")
            append("&redirect_uri=$encodedRedirectUri")
        }
    }

    /**
     * Stores the [token] for the cloud service.
     */
    public abstract fun storeToken(token: String)
}

/**
 * Authenticator using PKCE flow.
 *
 * https://www.oauth.com/oauth2-servers/pkce/
 */
public abstract class PkceAuthenticator(
    private val clientId: String,
    private val redirectUri: String,
    protected val codeVerifier: String
) : CloudAuthenticator {

    private val codeChallenge = SecurityUtil.buildCodeChallenge(codeVerifier)

    protected abstract val baseUrl: String
    protected abstract val scope: String

    public override fun buildUri(): String {
        val encodedRedirectUri = UrlEncoderUtil.encode(redirectUri)

        return buildString {
            append(baseUrl)
            append("?client_id=$clientId")
            if (scope.isNotEmpty()) {
                append("&scope=$scope")
            }
            append("&response_type=code")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=S256")
            append("&redirect_uri=$encodedRedirectUri")
        }
    }

    /**
     * @throws CloudServiceException
     */
    public abstract suspend fun exchangeCodeForToken(code: String): String
}