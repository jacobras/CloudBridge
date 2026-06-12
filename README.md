# CloudBridge

![Android](https://img.shields.io/badge/Android-green.svg?logo=android)
![JS](https://img.shields.io/badge/JavaScript-yellow.svg?logo=javascript)
![WASM](https://img.shields.io/badge/WebAssembly-purple.svg?logo=webassembly)
![Desktop](https://img.shields.io/badge/Desktop-blue.svg?logo=kotlin)

Multiple clouds, one Kotlin Multiplatform bridge. Currently supporting Android, web and desktop (
JVM), but iOS support is planned.

<img height="172" src="/docs/images/logo.png" alt = "CloudBridge Logo "/>

## ⚠️ Under construction

This library is not yet stable. The API will change and docs may be outdated.

## ✨ Features

* ⚡ **Unified**: One library to access Dropbox, Google Drive and OneDrive.
* 🪶 **Lightweight**: No need to integrate different SDKs for different platforms (
  see [Underlying dependencies](#-underlying-dependencies) below).
* 📱 **Cross-platform**: Supports Android, web and desktop (JVM); iOS is planned.
* 👥 **Multi-user**: Some official SDKs allow only one user, CloudBridge has no limit.

Limited access scopes by using _app folders_ are preferred by the library wherever possible.

## ☁️ Cloud Services

|                        | Mobile<br>(Android) | Mobile<br>(iOS) | Desktop<br>(JVM) | Web<br>(JS/WASM) |
|------------------------|---------------------|-----------------|------------------|------------------|
| **Dropbox**            | ✅                   | ⏳               | ✅                | ✅                |
| **Google Drive**       | ✅                   | ⏳               | ✅                | ✅                |
| **Microsoft OneDrive** | ✅                   | ⏳               | ✅                | ✅                |

✅ = Supported.<br>
⏳ = Planned.

See [Compatibility.md](docs/Compatibility.md) for important remarks about each service.

## 💾 Supported operations

* **Folders**
    * List content
    * Create/delete folder
* **Files**
    * Create/update/delete file
    * Download file content
* **User**
    * Get name and email address

See [Compatibility.md](docs/Compatibility.md) for details.

## 💿 Installation

The library is published to Maven Central.

```kotlin
dependencies {
    implementation("nl.jacobras:cloudbridge:0.5.0")
}
```

## 🚀 Quick Start

The main entry point is `CloudBridge.dropbox()`, `CloudBridge.googleDrive()` or
`CloudBridge.oneDrive()`.

Here's an example with Dropbox. First instantiate the service:

```kotlin
val service = CloudBridge.dropbox()
```

Then, have the user authenticate.

**Desktop**

```kotlin
val authServer = LocalAuthenticationServer()

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

**Web**

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

**Android**

Open the auth URL in a Custom Tab and capture the redirect via a deep link (`intent-filter`).
Then exchange the authorization code for a token:

```kotlin
// When user wants to authenticate:
val url = service.authenticate(
    clientId = "yourClientId",
    redirectUri = "yourRedirectUri" // e.g. a custom scheme like "your.app://oauth"
)
CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())

// In your Activity's onNewIntent, parse the "code" from the redirect Uri:
val code = intent.data?.getQueryParameter("code") ?: return
val token = service.completeAuthentication(
    clientId = "yourClientId",
    redirectUri = "yourRedirectUri",
    code = code
)
```

Securely store the token and pass it to the constructor of the service to use it.

### Listing files

```kotlin
val service = CloudBridge.dropbox(token)

try {
    service.listFiles()
} catch (e: CloudServiceException) {
    // Handle...
}
```

## 📐 Design decisions

### Privacy

The library only supports limited/private app folders, no full access.

### Paths

The library prefers to work with IDs over paths.

### Accounts

Only one account per service is supported as of now.

### Types

`id` and `path` variables are typed as much as possible, to prevent accidental mix-ups.

### Unified error handling

Dropbox will throw `409` when it can't find a path. Other services throw
`404` CloudBridge turns them both into `CloudServiceException.NotFoundException.`

_Feel free to open an issue if you have a different use case for any of these._

## 🔗 Underlying dependencies

All service APIs were written from scratch to avoid dependencies on SDKs.

This library uses:

* [Ktor](https://ktor.io/) and [Ktorfit](https://foso.github.io/Ktorfit/) for network requests.
* [Coroutines](https://github.com/Kotlin/kotlinx.coroutines) for concurrency.
* [KotlinCrypto hash](https://github.com/KotlinCrypto/hash) for SHA256 hashing.
* [urlencoder](https://github.com/ethauvin/urlencoder) for URL encoding.

Only on Android:

* [multiplatform-settings](https://github.com/russhwolf/multiplatform-settings) to temporarily
  persist the OAuth code verifier.