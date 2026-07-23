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

The redirect URL needs to be configured in the service's API console. For example,
`https://example.com/cloudbridge-callback`, where `https://example.com` matches your app's domain,
but there's no hard requirement for this structure.