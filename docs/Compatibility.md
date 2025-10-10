# Compatibility

|                  | Dropbox | Google Drive | OneDrive |
|------------------|---------|--------------|----------|
| List files       | ✅       | ✅            | ✅        |
| Create file      | ✅       | ✅            | ✅        |
| Get file by ID   | ✅       | ✅            | ❌        |
| Get file by path | ❌       | ❌            | ✅        |

<hr>

## ☁️ Dropbox

No remarks.

<hr>

## ☁️ Google Drive

### 🔑 Authentication

Doesn't support PKCE flow, only implicit grant. This means the token cannot be refreshed
when it expires and the user needs to re-authenticate.

https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow

### 📦 File/folder types

Folders in Google Drive are just files, but with the mimetype `application/vnd.google-apps.folder`.

### 📜 File/folder paths

Google Drive doesn't have the concept of paths. Instead, every file has a list of "parent file IDs".
In the past, there could be multiple, but now every file can have just one parent.

While a service like Dropbox, through CloudBridge, returns a path like `/work/memo/promotion.txt`, in
Google Drive it could be simply `/9xZDTanHiFcgg/promotion.txt`, no matter how nested the structure is.

Passing in a deeper path (for example `/id1/id2/file.txt`) has no effect, as CloudBridge only
takes the last part (`id2`) to store the file in.

<hr>

## ☁️ Microsoft OneDrive

No remarks.