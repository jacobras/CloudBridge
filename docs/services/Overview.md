# Cloud services

These subpages show how to authenticate on each platform, and any remarks/limitations of specific
services.

| Service                        | Data in app folder          | Support              | Delta support | Chunk size  |
|--------------------------------|-----------------------------|----------------------|---------------|-------------|
| [Dropbox](Dropbox.md)          | 👁️ Visible to users         | ✔️ All platforms     | ✅ Yes        | 8 MB [^4]   |
| [Google Drive](GoogleDrive.md) | 📦 Not visible to users[^1] | ✔️ All platforms     | ✅ Yes        | 10 MB [^5]  |
| [OneDrive](OneDrive.md)        | 👁️ Visible to users         | ✔️ All platforms     | ✅ Yes        | 10 MB [^6]  | 
| [WebDAV](WebDAV.md)            | 👁️ Visible to users         | ✔️ All platforms[^2] | ❌ No[^3]     | Not chunked |

- "All platforms" means Android, iOS, desktop (JVM) and web (JS/WASM).
- "Chunk size" is the size of each part in which files larger than 10 MB are uploaded.

[^1]: "App data not visible" means the user will not see the app files when opening the cloud folder
themselves (e.g. on user's desktop or in the web interface of Google
Drive). [More details](GoogleDrive.md#compatibility).
[^2]: WebDAV on web requires dealing
with [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS). Some providers, like
Fastmail, block requests from all domains.
[^3]: You could keep the last result of a call and compare a new result manually to get delta
support with WebDAV.
[^4]: A multiple of 4 MB,
[as required by Dropbox](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload-session-start).
[^5]: A multiple of 256 KB,
[as required by Google Drive]([https://developers.google.com/workspace/drive/api/guides/manage-uploads#uploading](https://developers.google.com/workspace/drive/api/guides/manage-uploads#uploading)).
[^6]: A multiple of 320 KB,
[as required by OneDrive](https://learn.microsoft.com/en-us/graph/api/driveitem-createuploadsession?view=graph-rest-1.0#best-practices).