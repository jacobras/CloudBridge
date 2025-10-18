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

## ☁️ Cloud Services

|                        | Mobile<br>(Android) | Desktop<br>(JVM) | Web<br>(JS/WASM) |
|------------------------|---------------------|------------------|------------------|
| **Dropbox**            | ⏳                   | ⏳                | ✅                |
| **Google Drive**       | ⏳                   | ⏳                | ✅                |
| **Microsoft OneDrive** | ⏳                   | ⏳                | ✅                |

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

## ⚠️ Under construction

This library is not yet stable. The API will change.

## 💿 Installation

The library is published to Maven Central.

```kotlin
dependencies {
    implementation("nl.jacobras:cloudbridge:0.4.0")
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
val service = CloudBridge.dropbox(clientId = "yourClientId")

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

`id` and `path` variables are typed as much as possible, to prevent
accidental mix-ups.

### Unified error handling

Dropbox will throw `409` when it can't find a path. Other services throw
`404` CloudBridge turns them both into `CloudServiceException.NotFoundException.`

_Feel free to open an issue if you have a different use case for any
of these._

## 🔗 Underlying dependencies

Next to [Kotlin Multiplatform](https://www.jetbrains.com/kotlin-multiplatform/), this library uses:

* [Ktor](https://ktor.io/) and [Ktorfit](https://foso.github.io/Ktorfit/) for network requests.
* [KotlinCrypto hash](https://github.com/KotlinCrypto/hash) for SHA256 hashing.
* [multiplatform-settings](https://github.com/russhwolf/multiplatform-settings) to persist tokens.
* [urlencoder](https://github.com/ethauvin/urlencoder) for URL encoding.