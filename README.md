# CloudBridge

![Android](https://img.shields.io/badge/Android-green.svg?logo=android)
![iOS](https://img.shields.io/badge/iOS-lightgray.svg?logo=apple)
![Desktop](https://img.shields.io/badge/Desktop-blue.svg?logo=kotlin)
![JS](https://img.shields.io/badge/JavaScript-yellow.svg?logo=javascript)
![WASM](https://img.shields.io/badge/WebAssembly-purple.svg?logo=webassembly)

Multiple clouds, one Kotlin Multiplatform bridge. Supporting Android, iOS, web and desktop (JVM).

<img height="172" src="/docs/assets/images/logo.png" alt = "CloudBridge Logo "/>

> [!WARNING]
> This library is not yet stable. The API will change and docs may be outdated.

## ✨ Features

* ⚡ **Unified**: One library to access Dropbox, Google Drive and OneDrive.
* 🪶 **Lightweight**: No need to integrate different SDKs for different platforms
  (see [Underlying dependencies](#-underlying-dependencies)).
* 📱 **Cross-platform**: Supports Android, iOS, web and desktop (JVM).
* 👥 **Multi-user**: Some official SDKs allow only one user, CloudBridge has no limit.
* 💥 **Unified error handling**: No different error codes to handle, but unified, typed errors.

Limited access scopes by using _app folders_ are preferred by the library wherever possible.
Furthermore, the library works with app folders, prefers IDs to paths and is strongly typed where
possible.

See <https://jacobras.github.io/CloudBridge/Vision> for details.

## ☁️ Cloud Services

|                        | Mobile<br>(Android) | Mobile<br>(iOS) | Desktop<br>(JVM) | Web<br>(JS/WASM) |
|------------------------|---------------------|-----------------|------------------|------------------|
| **Dropbox**            | ✅                   | ✅               | ✅                | ✅                |
| **Google Drive**       | ✅                   | ✅               | ✅                | ✅                |
| **Microsoft OneDrive** | ✅                   | ✅               | ✅                | ✅                |

✅ = Supported.

See specific service docs for important remarks about each service.

## 💿 Installation

The library is published to Maven Central.

```kotlin
dependencies {
    implementation("nl.jacobras:cloudbridge:0.7.0")
}
```

## 🚀 Quick Start

The main entry point is `CloudBridge.dropbox()`, `CloudBridge.googleDrive()` or
`CloudBridge.oneDrive()`.

Here's an example with Dropbox.

```kotlin
// 1: Instantiate a service
val service = CloudBridge.dropbox()

// 2: Authenticate (platform- and service-specific, see docs)
service.authenticate("clientId", "example://redirect-uri")

// 3: Ready for use!
service.listFiles("/".asFolderPath())
```

- See <https://jacobras.github.io/CloudBridge/services/Overview/> on how to authenticate each
  service on every platform.
- See <https://jacobras.github.io/CloudBridge/api/Overview/> for all available operations.

## 🔗 Underlying dependencies

All service APIs were written from scratch to avoid dependencies on SDKs.

This library uses:

* [Ktor](https://ktor.io/) and [Ktorfit](https://foso.github.io/Ktorfit/) for network requests.
* [Coroutines](https://github.com/Kotlin/kotlinx.coroutines) for concurrency.
* [KotlinCrypto hash](https://github.com/KotlinCrypto/hash) for SHA256 hashing.
* [urlencoder](https://github.com/ethauvin/urlencoder) for URL encoding.

Only on Android:

* [multiplatform-settings](https://github.com/russhwolf/multiplatform-settings) to temporarily
  persist the OAuth code verifier (Dropbox & OneDrive).
* [Google Identity Services](https://developers.google.com/identity/authorization/android)
  (`play-services-auth`) for Google Drive authorization.