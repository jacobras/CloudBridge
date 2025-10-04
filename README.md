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

Not yet published.

## 🔗 Underlying dependencies

Next to [Kotlin Multiplatform](https://www.jetbrains.com/kotlin-multiplatform/), this library uses:

* [Ktor](https://ktor.io/) and [Ktorfit](https://foso.github.io/Ktorfit/) for network requests.
* [KotlinCrypto hash](https://github.com/KotlinCrypto/hash) for SHA256 hashing.
* [urlencoder](https://github.com/ethauvin/urlencoder) for URL encoding.