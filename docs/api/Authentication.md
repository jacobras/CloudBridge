# Authentication/user info

## Checking authentication

```kotlin
val service = CloudBridge.dropbox(clientId = "yourClientId", token = token)

if (service.isAuthenticated()) {
    // Ready to use.
}
```

Call `setToken()` to attach or replace the access token used for authentication, for example after
loading a previously stored token:

```kotlin
service.setToken(storedToken)
```

## Token refresh

CloudBridge refreshes tokens automatically. You only need to store the updated token after it's
been refreshed:

```kotlin
val service = CloudBridge.dropbox(
    clientId = "yourClientId",
    token = storedToken,
    onTokenRefreshed = { token -> TODO("Store the token locally") }
)
```

The token is refreshed 1 minute before it expires (see `CloudAccessToken.expiresAt`). If a request
still fails with an authentication error, the token is refreshed and the request is retried once.
If refreshing fails (for example, because the user revoked access), a `NotAuthenticatedException`
is thrown and the user needs to re-authenticate.

!!! note

    Google Drive doesn't support refreshing tokens on web. See [Google Drive](../services/GoogleDrive.md#token-refresh) for details.

## Getting user info

```kotlin
try {
    val userInfo = service.getUserInfo()
    println(userInfo.name)
    println(userInfo.emailAddress)
} catch (e: CloudServiceException) {
    // Handle...
}
```