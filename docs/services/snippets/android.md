Set up a deeplink filter inside `AndroidManifest.xml`:

```xml
<!-- OAuth redirect deep link: com.example.myapp://cloudbridge-auth -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="com.example.myapp"
        android:host="cloudbridge-auth" />
</intent-filter>
```

This link also needs to be configured in your service's API Console.

Open the auth URL in a Custom Tab and capture the redirect. Then exchange the authorization code for a token:

```kotlin
// When user wants to authenticate:
val url = service.authenticate(
    clientId = "yourClientId",
    redirectUri = "yourRedirectUri" // e.g. a custom scheme like "com.example://cloudbridge-auth"
)
CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())

// In your Activity's onNewIntent (and onCreate), call:
val token = service.completeAuthentication(
    clientId = "yourClientId",
    redirectUri = "yourRedirectUri", // Needs to match the uri passed into authenticate()
    intentUri = uri
)
```

Securely store the token and pass it to the constructor of the service to use it.