# Compatibility

|                  | Dropbox | Google Drive | OneDrive |
|------------------|---------|--------------|----------|
| List files       | ✅       | ✅            | ✅        |
| Create file      | ✅       | ✅            | ✅        |
| Get file by ID   | ✅       | ✅            | ❌        |
| Get file by path | ❌       | ❌            | ✅        |

## ☁️ Service remarks

### Google Drive

Doesn't support PKCE flow, only implicit grant.

https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow