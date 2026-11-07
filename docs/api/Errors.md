# Error handling

All suspending calls can throw a `CloudBridgeException`.

## Common exceptions

These are expected in normal use.

| Exception                                         | Cause                                       |
|---------------------------------------------------|---------------------------------------------|
| `CloudBridgeException.ConflictException`          | An item with the same name already exists   |
| `CloudBridgeException.ConnectionException`        | A network error occurred                    |
| `CloudBridgeException.InsufficientSpaceException` | The account has no storage space left       |
| `CloudBridgeException.NotAuthenticatedException`  | The service has no valid access token (yet) |
| `CloudBridgeException.NotFoundException`          | The requested item could not be found       |
| `CloudBridgeException.RateLimitException`         | A [rate limit](RateLimiting.md) was hit     |

## Less common exceptions

These are not expected to happen during normal use.

| Exception                                       | Cause                                                                                     |
|-------------------------------------------------|-------------------------------------------------------------------------------------------|
| `CloudBridgeException.IllegalArgumentException` | An argument is invalid, e.g. a missing or wrong `sizeInBytes` for a large OneDrive upload |
| `CloudBridgeException.SinkWriteException`       | Writing the download to the `Sink` failed                                                 |
| `CloudBridgeException.SourceReadException`      | Reading the `Source` to upload failed, or it was already read                             |
| `CloudBridgeException.Unknown`                  | Any other unexpected error                                                                |