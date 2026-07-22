# ☁️ OneDrive

Start by instantiating the service:

```kotlin
val service = CloudBridge.oneDrive()
```

Then, authenticate using the platform-specific methods below. Once authenticated,
the [shared API](../api/Overview.md) is available.

## Authenticating

=== "Android"

    --8<-- "docs/services/snippets/android.md"

=== "iOS"

    --8<-- "docs/services/snippets/iOS.md"

=== "Desktop"

    --8<-- "docs/services/snippets/desktop.md"

=== "Web"

    --8<-- "docs/services/snippets/web.md"