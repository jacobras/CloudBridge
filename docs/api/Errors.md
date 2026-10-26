# Error handling

All suspending calls can throw a `CloudServiceException`.

## Common exceptions

These are expected in normal use.

| Exception                                          | Cause                                       |
|----------------------------------------------------|---------------------------------------------|
| `CloudServiceException.ConflictException`          | An item with the same name already exists   |
| `CloudServiceException.ConnectionException`        | A network error occurred                    |
| `CloudServiceException.InsufficientSpaceException` | The account has no storage space left       |
| `CloudServiceException.NotAuthenticatedException`  | The service has no valid access token (yet) |
| `CloudServiceException.NotFoundException`          | The requested item could not be found       |

## Less common exceptions

These are not expected to happen during normal use.

| Exception                                        | Cause                                                                                     |
|--------------------------------------------------|-------------------------------------------------------------------------------------------|
| `CloudServiceException.IllegalArgumentException` | An argument is invalid, e.g. a missing or wrong `sizeInBytes` for a large OneDrive upload |
| `CloudServiceException.SinkWriteException`       | Writing the download to the `Sink` failed                                                 |
| `CloudServiceException.SourceReadException`      | Reading the `Source` to upload failed                                                     |
| `CloudServiceException.Unknown`                  | Any other unexpected error                                                                |