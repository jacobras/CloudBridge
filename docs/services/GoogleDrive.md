# ☁️ Google Drive

Start by instantiating the service:

```kotlin
val service = CloudBridge.googleDrive()
```

Then, authenticate using the platform-specific methods below. Once authenticated,
the [shared API](../api/Overview.md) is available.

## Compatibility

=== "App Data folder visibility"

    The App Data folder is not shown to users. A user can only see the amount of space it takes up by going to the Drive site and navigating to _Settings_ » _Manage apps_.

=== "Duplication"

    Files and folders do not need unique names in Google Drive, so there can be duplicates.

=== "File types"

    Folders in Google Drive are just files, but with the mimetype `application/vnd.google-apps.folder`.

=== "Paths"

    Google Drive doesn't have the concept of paths. Instead, every file has a list of "parent
    file IDs". In the past, there could be multiple, but now every file can have just one
    parent.
    
    To offer a unified API, CloudBridge still builds a path for Drive files. It will contain
    the parent file ID and filename. For example, a nested folder could look like
    `/A2vXis2oKtcHO/9xZDTanHiFcgg/` and a file inside a folder could be
    `/9xZDTanHiFcgg/promotion.txt`.
    
    Passing in a deeper path on a file (for example `/id1/id2/file.txt`) has no effect, as
    only the last part (`id2`) is set as a parent to the file/folder.

## Authenticating

=== "Android"

    Google no longer supports custom-scheme redirects on Android, so the library has to use Google
    Identity Services instead. A wrapper around it is provided:
    
    ```kotlin
    val googleDriveAuthenticator = GoogleDriveAuthenticator(
        activity = this,
        onSuccess = { token ->
            service.setToken(token)
            TODO("Store the token locally")
        },
        onDenied = { TODO() },
        onFailure = { error -> TODO() }
    )
    ```
    
    Securely store the token and pass it to the constructor of the service to use it.

=== "❌ iOS"

    Not yet supported, see <https://github.com/jacobras/CloudBridge/issues/78>

=== "Desktop"

    Note that Google Drive requires a secret on desktop. This is provided in the Google Drive API console.
    
    ```kotlin
    val authServer = LocalAuthenticationServer()
    
    // Build auth URL and open it in the browser
    val url = service.authenticate(
        authServer = authServer,
        clientId = "yourClientId",
        clientSecret = "yourSecret",
        onSuccess = { token ->
            service.setToken(token)
            TODO("Store the token locally")
        }
    )
    openBrowser(url)
    ```

=== "Web"

    Google Drive doesn't support PKCE flow on web, only implicit grant. This means the token cannot be
    refreshed when it expires and the user needs to re-authenticate.
    
    <https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow>
    
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