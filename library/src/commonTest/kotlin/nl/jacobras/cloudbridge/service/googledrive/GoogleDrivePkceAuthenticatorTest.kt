@file:OptIn(InternalCloudBridgeApi::class)

package nl.jacobras.cloudbridge.service.googledrive

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.internal.CloudBridgeInternals.applyDefaults
import nl.jacobras.cloudbridge.internal.InternalCloudBridgeApi
import kotlin.test.Test

class GoogleDrivePkceAuthenticatorTest {

    @Test
    fun `authorization URI forces consent to always get a refresh token`() {
        val clientId = "clientId"
        val authenticator = GoogleDrivePkceAuthenticator(
            api = GoogleDriveService(
                clientInfo = ClientInfo(
                    clientId = clientId,
                    googleDriveDesktopSecret = null
                ),
                token = null
            ).api,
            clientId = clientId,
            clientSecret = null,
            redirectUri = "http://localhost:8080",
            codeVerifier = "DdiKuAnfGnotN1d_Qh5xfTcRcz936rrmFqyelltHIaDwgOjP"
        )

        assertThat(authenticator.buildPkceUri()).contains("&prompt=consent")
    }

    @Test
    fun `code exchange without refresh token still links`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"access_token":"access","expires_in":3599}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val clientId = "clientId"
        val authenticator = GoogleDrivePkceAuthenticator(
            api = GoogleDriveService(
                clientInfo = ClientInfo(clientId = clientId),
                token = null,
                httpClientOverride = HttpClient(engine) { applyDefaults() }
            ).api,
            clientId = clientId,
            clientSecret = null,
            redirectUri = "http://localhost:8080",
            codeVerifier = "DdiKuAnfGnotN1d_Qh5xfTcRcz936rrmFqyelltHIaDwgOjP"
        )

        val token = authenticator.exchangeCodeForToken("code")

        assertThat(token.accessToken).isEqualTo("access")
        assertThat(token.refreshToken).isNull()
    }

    @Test
    fun `code exchange error is mapped to CloudBridgeException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_request"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val clientId = "clientId"
        val authenticator = GoogleDrivePkceAuthenticator(
            api = GoogleDriveService(
                clientInfo = ClientInfo(clientId = clientId),
                token = null,
                httpClientOverride = HttpClient(engine) { applyDefaults() }
            ).api,
            clientId = clientId,
            clientSecret = null,
            redirectUri = "http://localhost:8080",
            codeVerifier = "DdiKuAnfGnotN1d_Qh5xfTcRcz936rrmFqyelltHIaDwgOjP"
        )

        assertFailure { authenticator.exchangeCodeForToken("code") }
            .isInstanceOf<CloudBridgeException.NotAuthenticatedException>()
    }
}