# Introduction

Welcome to _**CloudBridge**_! Multiple clouds, one Kotlin Multiplatform bridge. Supporting Android,
iOS, web and desktop (JVM).

## Installation

The library is published to Maven Central.

```kotlin
dependencies {
    implementation("nl.jacobras:cloudbridge:0.5.0")
}
```

## Usage

In general, CloudBridge works as follows:

```kotlin
// 1: Instantiate a service
val service = CloudBridge.dropbox()

// 2: Authenticate (platform- and service-specific)
val token = service.authenticate("clientId", "example://redirect-uri")
service.setToken(token)

// 3: Ready for use!
service.listFiles()
```

See [Cloud services](services/Overview.md) on how to authenticate each service on every platform.

See [shared API](api/Overview.md) for all available operations.

See the [demo app source](https://github.com/jacobras/CloudBridge/tree/main/demo) for more example code.