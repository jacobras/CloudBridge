```kotlin
val authServer = LocalAuthenticationServer() // optional parameter: `port=8080`

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

The redirect URL needs to be configured in the service's API console. Since CloudBridge on desktop
uses a loopback server, the URL will be `http://localhost:8080` (or whichever port you specify).