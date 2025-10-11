# Compatibility

## ☁️ Dropbox

No remarks.

<hr>

## ☁️ Google Drive

### 🔑 Authentication

Doesn't support PKCE flow on web, only implicit grant. This means the token cannot be
refreshed when it expires and the user needs to re-authenticate.

https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow

### 📦 File/folder types

Folders in Google Drive are just files, but with the mimetype
`application/vnd.google-apps.folder`.

### 📜 File/folder paths

Google Drive doesn't have the concept of paths. Instead, every file has a list of "parent
file IDs". In the past, there could be multiple, but now every file can have just one
parent.

To offer a unified API, CloudBridge still builds a path for Drive files. It will contain
the parent file ID and filename. For example, a nested folder could look like
`/A2vXis2oKtcHO/9xZDTanHiFcgg/` and a file inside a folder could be
`/9xZDTanHiFcgg/promotion.txt`.

Passing in a deeper path on a file (for example `/id1/id2/file.txt`) has no effect, as
only the last part (`id2`) is set as a parent to the file/folder.

<hr>

## ☁️ Microsoft OneDrive

No remarks.