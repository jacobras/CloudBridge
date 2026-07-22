```kotlin
val authServer = LocalAuthenticationServer()

// Build auth URL and open it in the browser
val url = service.authenticate(
    authServer = authServer,
    clientId = "yourClientId",
    onSuccess = { token ->
        service.setToken(token)
        TODO("Store the token locally")
    }
)
openBrowser(url)
```