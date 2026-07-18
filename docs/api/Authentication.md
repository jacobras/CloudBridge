# Authentication/user info

## Checking authentication

```kotlin
val service = CloudBridge.dropbox(token)

if (service.isAuthenticated()) {
    // Ready to use.
}
```

Call `setToken()` to attach or replace the access token used for authentication, for example after
loading a previously stored token:

```kotlin
service.setToken(storedToken)
```

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