# Rate limiting

CloudBridge uses rate limiting, so you can make as many calls as you like.

Here are the default (non-changeable) settings:

| Service                         | Max. concurrent requests | Max. requests per second |
|---------------------------------|--------------------------|--------------------------|
| Dropbox, Google Drive, OneDrive | 5                        | 5.0                      |
| WebDAV                          | 10                       | 10.0                     |

The limits count HTTP requests, so a chunked upload counts separately. The limits are shared per
service, so, for example, multiple accounts to Dropbox will together do max 5 concurrent requests.

## Retries

A service can still respond with a rate limit, depending on internal rules at the cloud providers.
Requests are automatically retried up to 3 times, backing off exponentially.

A `CloudServiceException.RateLimitException` is thrown, with `retryAfter` (if provided), when:

* The retries are exhausted.
* The service asks to wait longer than 30 seconds.
* The request was the WebDAV upload of a `Source`, as the source can only be read once. Uploading a
  `String` to WebDAV can be retried.