```kotlin
// Always call this:
val token = service.completeAuthentication()
if (token != null) {
    service.setToken(token)
}

// When user wants to authenticate:
val uri = service.authenticate(
    clientId = "yourClientId",
    redirectUri = "yourRedirectUri"
)
window.location.href = uri
```