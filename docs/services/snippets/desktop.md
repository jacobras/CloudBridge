```kotlin
val authServer = LocalAuthenticationServer() // Optionally, pass in a port

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