package nl.jacobras.cloudbridge.auth

import net.thauvin.erik.urlencoder.UrlEncoderUtil
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.security.SecurityUtil

/**
 * Can be used to authorize a user.
 */
public abstract class CloudAuthenticator(
    private val clientId: String,
    private val redirectUri: String
) {
    protected abstract val baseUrl: String
    protected abstract val scope: String
    protected abstract val responseType: String

    /**
     * Builds an authorization URI. Navigate the user to this URI to
     * start the authorization flow.
     */
    protected fun buildUri(codeChallenge: String): String {
        val encodedRedirectUri = UrlEncoderUtil.encode(redirectUri)

        return buildString {
            append(baseUrl)
            append("?client_id=$clientId")
            append("&response_type=$responseType")
            append("&redirect_uri=$encodedRedirectUri")

            if (scope.isNotEmpty()) {
                append("&scope=$scope")
            }

            if (codeChallenge.isNotEmpty()) {
                append("&code_challenge=$codeChallenge")
                append("&code_challenge_method=S256")
            }
        }
    }

    public abstract fun buildUri(): String
}

/**
 * Authenticator using Implicit Flow.
 *
 * https://www.oauth.com/oauth2-servers/single-page-apps/implicit-flow/
 */
public abstract class ImplicitAuthenticator(
    clientId: String,
    redirectUri: String
) : CloudAuthenticator(
    clientId = clientId,
    redirectUri = redirectUri
) {
    override val responseType: String = "token"

    public override fun buildUri(): String = buildUri(codeChallenge = "")

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
    clientId: String,
    redirectUri: String,
    protected val codeVerifier: String
) : CloudAuthenticator(
    clientId = clientId,
    redirectUri = redirectUri
) {
    override val responseType: String = "code"
    private val codeChallenge = SecurityUtil.buildCodeChallenge(codeVerifier)

    public override fun buildUri(): String = buildUri(codeChallenge = codeChallenge)

    /**
     * @throws CloudServiceException
     */
    public abstract suspend fun exchangeCodeForToken(code: String): String
}