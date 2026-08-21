# Cloud services

These subpages show how to authenticate on each platform, and any remarks/limitations of specific
services.

| Service                        | Data in app folder          | Support              |
|--------------------------------|-----------------------------|----------------------|
| [Dropbox](Dropbox.md)          | 👁️ Visible to users        | ✔️ All platforms     |
| [Google Drive](GoogleDrive.md) | 📦 Not visible to users[^1] | ✔️ All platforms     |
| [OneDrive](OneDrive.md)        | 👁️ Visible to users        | ✔️ All platforms     | 
| [WebDAV](WebDAV.md)            | 👁️ Visible to users        | ✔️ All platforms[^2] |

✔️ _All platforms_ means Android, iOS, desktop (JVM) and web (JS/WASM).

[^1]: _App data not visible_ means the user will not see the app files when opening the cloud folder
themselves (e.g. on user's desktop or in the web interface of Google
Drive). [More details](GoogleDrive.md#compatibility).
[^2]: WebDAV on web requires dealing
with [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS). Some providers, like
Fastmail, block requests from all domains.