# ☁️ WebDAV

Start by instantiating the service:

```kotlin
val service = CloudBridge.webDav(
    WebDavCredentials(
        serverUrl = "https://example.com/webdav",
        username = "your-username",
        password = "your-password"
    )
)
```

Then the [shared API](../api/Overview.md) is available.