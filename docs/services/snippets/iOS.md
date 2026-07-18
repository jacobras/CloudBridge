```kotlin
service.authenticate(
    clientId = "yourClientId",
    redirectUri = "yourRedirectUri" // e.g. a custom scheme like "com.example://cloudbridge-auth"
)?.let { token ->
    service.setToken(token)
    TODO("Store the token locally")
}
```