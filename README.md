# CloudBridge

![JS](https://img.shields.io/badge/JavaScript-yellow.svg?logo=javascript)
![WASM](https://img.shields.io/badge/WebAssembly-purple.svg?logo=webassembly)

Multiple clouds, one Kotlin Multiplatform bridge. Currently only supporting
web, but desktop and Android support are planned.

<img height="172" src="/docs/images/logo.png"/>

## ✨ Features

* ⚡ **Unified**: One library to access Dropbox and OneDrive.
* 🪶 **Lightweight**: No need to integrate different SDKs for different platforms.
* 📱 **Cross-platform**: Currently supports web, but mobile (Android) and
  desktop (JVM) are planned.

## Cloud Providers

|                        | Mobile<br>(Android) | Desktop<br>(JVM) | Web<br>(JS/WASM) |
|------------------------|---------------------|------------------|------------------|
| **Dropbox**            | ⏳                   | ⏳                | ✅                |
| **Microsoft OneDrive** | ⏳                   | ⏳                | ✅                |

⏳ = Not yet supported.

## 💾 Supported operations

* List files

## ⚠️ Under construction

This library is not yet stable. The API will change.

## 🚀 Quick Start

The library is not yet published.

### Authenticating

The main entry point is `CloudBridge.dropbox` or `CloudBridge.oneDrive`.

```kotlin
val authenticator = CloudBridge.dropbox.getAuthenticator(
    clientId = "yourClientId",
    redirectUri = "yourRedirectUri",
    codeVerifier = "" // Pass in existing codeVerifier here, or "" if not available yet.
)
val authenticateUrl = authenticator.buildUrl()

// Now save `authenticator.codeVerifier` to storage
// and redirect user to `authenticateUrl`
```

### Getting token

```kotlin
val token = authenticator.getToken(
    redirectUri = "yourRedirectUri", // Must match exactly the one passed in before
    code = code // `code` param extracted from the redirect URL
)
```

### Listing files

```kotlin
val service = CloudBridge.dropbox.getService(
    clientId = "yourClientId",
    token = "the token obtained above"
)

try {
    service.listFiles()
} catch (e: Exception) {
    // Handle...
}
```

## 🔗 Underlying dependencies

Next to [Kotlin Multiplatform](https://www.jetbrains.com/kotlin-multiplatform/), this library uses:

* [Ktor](https://ktor.io/) and [Ktorfit](https://foso.github.io/Ktorfit/) for network requests.
* [KotlinCrypto hash](https://github.com/KotlinCrypto/hash) for SHA256 hashing.
* [urlencoder](https://github.com/ethauvin/urlencoder) for URL encoding.