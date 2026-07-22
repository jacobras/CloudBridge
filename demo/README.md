# CloudBridge Demo

The demo is split into platform modules that share `:demo:shared`:

- `:demo:android` — Android app
- `:demo:desktop` — Desktop (JVM) app
- `:demo:iosApp` — iOS app (Xcode project)
- `:demo:web` — Web (JS/Wasm) app

### Android

Run from Android Studio.

### iOS

Run from Android Studio or Xcode.

### Desktop

`gradlew :demo:desktop:run`

### Web

`gradlew :demo:web:jsBrowserDevelopmentRun`