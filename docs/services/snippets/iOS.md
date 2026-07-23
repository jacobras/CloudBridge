```kotlin
service.authenticate(
    clientId = "yourClientId",
    redirectUri = "com.example.app://cloudbridge-auth" // change to your app
)?.let { token ->
    service.setToken(token)
    TODO("Store the token locally")
}
```

The redirect URL needs to be configured in the service's API console. The recommended format for
CloudBridge is `com.example.app://cloudbridge-auth`, where `com.example.app` matches your app's
identifier, but there's no hard requirement for this structure.