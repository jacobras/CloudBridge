# Error handling

All suspending calls can throw a `CloudServiceException`:

| Exception                                         | Cause                                     |
|---------------------------------------------------|-------------------------------------------|
| `CloudServiceException.NotAuthenticatedException` | The service has no valid access token yet |
| `CloudServiceException.NotFoundException`         | The requested item could not be found     |
| `CloudServiceException.ConnectionException`       | A network/IO error occurred               |
| `CloudServiceException.Unknown`                   | Any other unexpected error                |