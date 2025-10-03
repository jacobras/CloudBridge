package nl.jacobras.cloudbridge

import nl.jacobras.cloudbridge.security.SecurityUtil

public abstract class CloudAuthenticator(
    public val codeVerifier: String
) {
    protected val codeChallenge: String = SecurityUtil.buildCodeChallenge(codeVerifier)
}