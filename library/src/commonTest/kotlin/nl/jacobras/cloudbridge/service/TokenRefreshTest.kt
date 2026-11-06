@file:OptIn(InternalCloudBridgeApi::class)

package nl.jacobras.cloudbridge.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.auth.ClientInfo
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.auth.OAuthTokenRefresher
import nl.jacobras.cloudbridge.auth.TokenRefreshEndpoint
import nl.jacobras.cloudbridge.auth.TokenRefresher
import nl.jacobras.cloudbridge.auth.TokenResponse
import nl.jacobras.cloudbridge.auth.toCloudAccessToken
import nl.jacobras.cloudbridge.internal.CloudBridgeInternals.applyDefaults
import nl.jacobras.cloudbridge.internal.InternalCloudBridgeApi
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.util.testClientInfo
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TokenRefreshTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@TokenRefreshTest.now
    }

    @Test
    fun `token response is converted with absolute expiry`() {
        val token = TokenResponse(accessToken = "a", refreshToken = "r", expiresInSeconds = 3600)
            .toCloudAccessToken(now)

        assertThat(token).isEqualTo(CloudAccessToken("a", "r", now + 1.hours))
    }

    @Test
    fun `refresh 1 minute before expiry`() = runTest {
        val engine = dropboxEngine()
        val refreshed = mutableListOf<CloudAccessToken>()
        val service = dropbox(
            token = CloudAccessToken("old", "refresh", expiresAt = now + 59.seconds),
            engine = engine,
            onTokenRefreshed = { refreshed += it }
        )

        service.getUserInfo()

        assertThat(engine.requestHistory.map { it.url.encodedPath }).containsExactly(
            "/oauth2/token",
            "/2/users/get_current_account"
        )
        // Sent in the body, so the refresh token doesn't end up in request logs.
        assertThat(engine.requestHistory[0].url.parameters.isEmpty()).isTrue()
        assertThat(engine.requestHistory[0].formParameters()).isEqualTo(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to "refresh",
                "client_id" to "testClient"
            )
        )
        assertThat(engine.requestHistory[0].headers[HttpHeaders.Authorization]).isNull()
        assertThat(engine.requestHistory[1].headers[HttpHeaders.Authorization]).isEqualTo("Bearer new")
        // Dropbox doesn't return a new refresh token, so the old one is kept.
        assertThat(refreshed).containsExactly(
            CloudAccessToken("new", "refresh", now + 4.hours)
        )
    }

    @Test
    fun `no refresh when token expires in more than 1 minute`() = runTest {
        val engine = dropboxEngine(validToken = "old")
        val service = dropbox(
            token = CloudAccessToken("old", "refresh", expiresAt = now + 61.seconds),
            engine = engine
        )

        service.getUserInfo()

        assertThat(engine.requestHistory.map { it.url.toString() }).containsExactly(
            "https://api.dropboxapi.com/2/users/get_current_account"
        )
    }

    @Test
    fun `refresh and retry after 401`() = runTest {
        val engine = dropboxEngine()
        val refreshed = mutableListOf<CloudAccessToken>()
        val service = dropbox(
            token = CloudAccessToken("old", "refresh"),
            engine = engine,
            onTokenRefreshed = { refreshed += it }
        )

        service.getUserInfo()

        assertThat(engine.requestHistory.map { it.url.encodedPath }).containsExactly(
            "/2/users/get_current_account",
            "/oauth2/token",
            "/2/users/get_current_account"
        )
        assertThat(refreshed.map { it.accessToken }).containsExactly("new")
    }

    @Test
    fun `parallel calls failing with 401 refresh only once`() = runTest {
        val engine = dropboxEngine()
        val service = dropbox(token = CloudAccessToken("old", "refresh"), engine = engine)

        List(3) { async { service.getUserInfo() } }.awaitAll()

        assertThat(engine.requestHistory.count { it.url.encodedPath == "/oauth2/token" }).isEqualTo(
            1
        )
    }

    @Test
    fun `throw NotAuthenticatedException when refreshed token is rejected too`() = runTest {
        val engine = dropboxEngine(validToken = "never")
        val service = dropbox(token = CloudAccessToken("old", "refresh"), engine = engine)

        assertFailure { service.getUserInfo() }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(engine.requestHistory.map { it.url.encodedPath }).containsExactly(
            "/2/users/get_current_account",
            "/oauth2/token",
            "/2/users/get_current_account"
        )
    }

    @Test
    fun `throw NotAuthenticatedException when refresh token is revoked`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/oauth2/token") {
                respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
            } else {
                respondJson("{}", HttpStatusCode.Unauthorized)
            }
        }
        val refreshed = mutableListOf<CloudAccessToken>()
        val service = DropboxService(
            clientInfo = testClientInfo,
            token = CloudAccessToken("old", "refresh"),
            httpClientOverride = HttpClient(engine) { applyDefaults() },
            rateLimiter = null,
            tokenRefresher = OAuthTokenRefresher(testClientInfo),
            onTokenRefreshed = { refreshed += it },
            clock = clock
        )

        assertFailure { service.getUserInfo() }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(refreshed).isEmpty()
    }

    /**
     * Only happens with Google Drive.
     */
    @Test
    fun `no refresh without token refresher`() = runTest {
        val engine = dropboxEngine()
        val service = GoogleDriveService(
            clientInfo = testClientInfo,
            token = CloudAccessToken("old", "refresh", expiresAt = now),
            httpClientOverride = HttpClient(engine) { applyDefaults() },
            rateLimiter = null,
            tokenRefresher = null,
            clock = clock
        )

        assertFailure { service.getUserInfo() }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(engine.requestHistory.map { it.url.encodedPath }).containsExactly(
            "/drive/v3/about"
        )
    }

    @Test
    fun `no refresh without refresh token`() = runTest {
        val engine = dropboxEngine()
        val service = dropbox(token = CloudAccessToken("old", expiresAt = now), engine = engine)

        assertFailure { service.getUserInfo() }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(engine.requestHistory.map { it.url.encodedPath }).containsExactly(
            "/2/users/get_current_account"
        )
    }

    @Test
    fun `Google Drive sends client secret when refreshing`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "https://oauth2.googleapis.com/token" -> respondJson(
                    """{"access_token":"new","expires_in":3599}"""
                )
                else -> if (request.headers[HttpHeaders.Authorization] == "Bearer new") {
                    respondJson("""{"user":{}}""")
                } else {
                    respondJson("{}", HttpStatusCode.Unauthorized)
                }
            }
        }
        val clientInfo = ClientInfo("client", googleDriveDesktopSecret = "secret")
        val service = GoogleDriveService(
            clientInfo = clientInfo,
            token = CloudAccessToken("old", "refresh"),
            httpClientOverride = HttpClient(engine) { applyDefaults() },
            rateLimiter = null,
            tokenRefresher = OAuthTokenRefresher(clientInfo),
            clock = clock
        )

        service.getUserInfo()

        val refreshRequest = engine.requestHistory.single { it.url.host == "oauth2.googleapis.com" }
        assertThat(refreshRequest.formParameters()).isEqualTo(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to "refresh",
                "client_id" to "client",
                "client_secret" to "secret"
            )
        )
    }

    @Test
    fun `OneDrive uses rotated refresh token`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "https://login.microsoftonline.com/common/oauth2/v2.0/token" -> respondJson(
                    """{"access_token":"new","refresh_token":"rotated","expires_in":3600}"""
                )
                else -> if (request.headers[HttpHeaders.Authorization] == "Bearer new") {
                    respondJson("{}")
                } else {
                    respondJson("{}", HttpStatusCode.Unauthorized)
                }
            }
        }
        val refreshed = mutableListOf<CloudAccessToken>()
        val service = OneDriveService(
            clientInfo = testClientInfo,
            token = CloudAccessToken("old", "refresh"),
            httpClientOverride = HttpClient(engine) { applyDefaults() },
            rateLimiter = null,
            tokenRefresher = OAuthTokenRefresher(testClientInfo),
            onTokenRefreshed = { refreshed += it },
            clock = clock
        )

        service.getUserInfo()

        assertThat(refreshed).containsExactly(CloudAccessToken("new", "rotated", now + 1.hours))
    }

    @Test
    fun `refresher without refresh token refreshes after 401`() = runTest {
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer n3w") {
                respondJson("""{"user":{}}""")
            } else {
                respondJson("{}", HttpStatusCode.Unauthorized)
            }
        }
        val refreshed = mutableListOf<CloudAccessToken>()
        val outdatedTokens = mutableListOf<CloudAccessToken>()
        val service = GoogleDriveService(
            clientInfo = testClientInfo,
            token = CloudAccessToken("0ld"),
            httpClientOverride = HttpClient(engine) { applyDefaults() },
            rateLimiter = null,
            tokenRefresher = FakeTokenRefresher {
                outdatedTokens += it
                CloudAccessToken("n3w")
            },
            onTokenRefreshed = { refreshed += it },
            clock = clock
        )

        service.getUserInfo()

        assertThat(outdatedTokens).containsExactly(CloudAccessToken("0ld"))
        assertThat(refreshed).containsExactly(CloudAccessToken("n3w"))
        assertThat(engine.requestHistory.map { it.headers[HttpHeaders.Authorization] })
            .containsExactly("Bearer 0ld", "Bearer n3w")
    }

    @Test
    fun `refresher failing throws NotAuthenticatedException`() = runTest {
        val engine = MockEngine { respondJson("{}", HttpStatusCode.Unauthorized) }
        val service = GoogleDriveService(
            clientInfo = testClientInfo,
            token = CloudAccessToken("old"),
            httpClientOverride = HttpClient(engine) { applyDefaults() },
            rateLimiter = null,
            tokenRefresher = FakeTokenRefresher {
                throw CloudServiceException.NotAuthenticatedException()
            },
            clock = clock
        )

        assertFailure { service.getUserInfo() }
            .isInstanceOf<CloudServiceException.NotAuthenticatedException>()
        assertThat(engine.requestHistory.size).isEqualTo(1)
    }

    /**
     * Mock Dropbox that accepts only [validToken] and hands out "new" when refreshing.
     */
    private fun dropboxEngine(validToken: String = "new") = MockEngine { request ->
        when (request.url.encodedPath) {
            "/oauth2/token" -> respondJson("""{"access_token":"new","expires_in":14400}""")
            else -> if (request.headers[HttpHeaders.Authorization] == "Bearer $validToken") {
                respondJson("{}")
            } else {
                respondJson(
                    """{"error":{".tag":"expired_access_token"}}""",
                    HttpStatusCode.Unauthorized
                )
            }
        }
    }

    private fun dropbox(
        token: CloudAccessToken,
        engine: MockEngine,
        onTokenRefreshed: ((CloudAccessToken) -> Unit)? = null
    ) = DropboxService(
        clientInfo = testClientInfo,
        token = token,
        httpClientOverride = HttpClient(engine) { applyDefaults() },
        rateLimiter = null,
        tokenRefresher = OAuthTokenRefresher(testClientInfo),
        onTokenRefreshed = onTokenRefreshed,
        clock = clock
    )

    /**
     * [TokenRefresher] that doesn't use a refresh token, like `GoogleIdentityTokenRefresher`.
     */
    private class FakeTokenRefresher(
        private val newToken: (outdated: CloudAccessToken) -> CloudAccessToken
    ) : TokenRefresher {
        override suspend fun refresh(
            outdated: CloudAccessToken,
            endpoint: TokenRefreshEndpoint,
            clock: Clock
        ): CloudAccessToken = newToken(outdated)
    }

    private fun HttpRequestData.formParameters(): Map<String, String> {
        val form = (body as FormDataContent).formData
        return form.names().associateWith { form[it]!! }
    }

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpResponseData = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}