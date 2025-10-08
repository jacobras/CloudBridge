# CloudBridge

![JS](https://img.shields.io/badge/JavaScript-yellow.svg?logo=javascript)
![WASM](https://img.shields.io/badge/WebAssembly-purple.svg?logo=webassembly)

Multiple clouds, one Kotlin Multiplatform bridge. Currently only supporting
web, but desktop and Android support are planned.

<img height="172" src="/docs/images/logo.png"/>

## ✨ Features

* ⚡ **Unified**: One library to access Dropbox, Google Drive and OneDrive.
* 🪶 **Lightweight**: No need to integrate different SDKs for different platforms.
* 📱 **Cross-platform**: Currently supports web, but mobile (Android) and
  desktop (JVM) are planned.

Limited access scopes by using _app folders_ are preferred by the library wherever possible.

## ☁️ Cloud Providers

|                        | Mobile<br>(Android) | Desktop<br>(JVM) | Web<br>(JS/WASM) |
|------------------------|---------------------|------------------|------------------|
| **Dropbox**            | ⏳                   | ⏳                | ✅                |
| **Google Drive**       | ⏳                   | ⏳                | ✅                |
| **Microsoft OneDrive** | ⏳                   | ⏳                | ✅                |

✅ = Supported.<br>
⏳ = Planned.

## 💾 Supported operations

* List files
* Download file
* Upload file

## ⚠️ Under construction

This library is not yet stable. The API will change.

## 💿 Installation

The library is published to Maven Central.

```kotlin
dependencies {
    implementation("nl.jacobras:cloudbridge:0.0.1")
}
```

## 🚀 Quick Start

The main entry point is `CloudBridge.dropbox()`, `CloudBridge.googleDrive()` or `CloudBridge.oneDrive()`.

Here's an example with Dropbox. First instantiate the service:

```kotlin
val service = CloudBridge.dropbox(clientId = "yourClientId")
```

Then, have the user authenticate on the authenticate URL:

```kotlin
val authenticator = service.getAuthenticator(redirectUri = "yourRedirectUri")
val authenticateUrl = authenticator.buildUrl()

// Now redirect the user to `authenticateUrl`
```

The user will grant access and get redirected to your redirect URI. Here, read
the `?code=xxx` parameter from the URL and pass it to the `authenticator`:

```kotlin
authenticator.exchangeCodeForToken(code = code)
```

Now the service is ready to be used!

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
* [multiplatform-settings](https://github.com/russhwolf/multiplatform-settings) to persist tokens.
* [urlencoder](https://github.com/ethauvin/urlencoder) for URL encoding.