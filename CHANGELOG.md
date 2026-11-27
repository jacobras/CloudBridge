# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.15.0] - 2026-11-27

### Changed

- **Breaking:** `listFiles()` is now paged. It takes an optional `cursor` and returns a
  `ListResult` with `items` and a `nextCursor` (`null` on the last page). Pages hold up to about
  200 items. See [Listing files](https://jacobras.github.io/CloudBridge/api/Files/#listing-files).
- Dropbox and OneDrive now also set `prompt=consent`, like Google Drive, so a user can choose the
  desired account when connecting.

### Fixed

- `completeAuthentication()` on Android and web now throw `CloudBridgeException` instead of raw Ktor
  exceptions.
- OneDrive's `listFiles()` now returns correct relative paths
  (fixes [#18](https://github.com/jacobras/CloudBridge/issues/18)).
- `listFiles()` on Dropbox, Google Drive and OneDrive silently dropped items beyond the first page
  (100 items on Google Drive, 200 on OneDrive). These can now be fetched with `nextCursor`.

## [0.14.0] - 2026-11-18

### Added

- `maxConcurrentWrites` to `RateLimiter`. Only set by Dropbox for now (to `1`), to fix excessive
  rate limiting ([#124](https://github.com/jacobras/CloudBridge/pull/124)).
- `Logger.warn(message, throwable)`, used for failures like authentication errors. Defaults to
  `log()`; override it to log at warning level
  ([#128](https://github.com/jacobras/CloudBridge/pull/128)).
- `NotAuthenticatedException` now has the underlying error as `cause` (like the service's response
  with `invalid_grant`) ([#128](https://github.com/jacobras/CloudBridge/pull/128)).

### Changed

- **Breaking:** renamed `SetupException` to `ServiceSetupException`
  ([#127](https://github.com/jacobras/CloudBridge/pull/127)).

### Fixed

- Linking Google Drive on Android no longer fails with `NotAuthenticatedException` when Google
  Identity Services returns a cached access token that Google no longer accepts. The token is
  cleared and authorization is requested once more
  ([#128](https://github.com/jacobras/CloudBridge/pull/128)).
- Google Drive file revisions are now based on `md5Checksum` instead of `version`, which also
  changes on server-side changes outside the library (for example right after every upload)
  ([#125](https://github.com/jacobras/CloudBridge/pull/125)).
- WebDAV folder IDs no longer end with a trailing slash, so they're the same whether the server adds
  one ([#127](https://github.com/jacobras/CloudBridge/pull/127)).
- WebDAV `createFolder` now throws `ConflictException` instead of `Unknown` when the folder
  already exists ([#127](https://github.com/jacobras/CloudBridge/pull/127)).
- WebDAV moves throw new `WebDavMoveException` instead of `Unknown` when the server rejects MOVE
  with 405 or 501 ([#127](https://github.com/jacobras/CloudBridge/pull/127)).
- WebDAV moves and `createFolder` throw `NotFoundException` instead of `Unknown` when the
  (destination's) parent folder doesn't exist
  ([#127](https://github.com/jacobras/CloudBridge/pull/127)).

## [0.13.0] - 2026-11-17

### Added

- `GoogleDriveService::getFileAppProperties(id)`
  ([#123](https://github.com/jacobras/CloudBridge/pull/123)) for apps migrating away from this.

## [0.12.1] - 2026-11-16

### Changed

- Set up Java/Kotlin compatibility ([#121](https://github.com/jacobras/CloudBridge/pull/121)).

## [0.12.0] - 2026-11-13

### Added

- `name` and `parent` to `Path`, and child constructors to build a `Path`
  ([#119](https://github.com/jacobras/CloudBridge/pull/119)).

### Fixed

- Root folder handling when building a `Path` with a parent folder.

## [0.11.0] - 2026-11-07

### Added

- Rate limiting, including retries on rate limiting. Services throw `RateLimitException` when rate
  limited ([#115](https://github.com/jacobras/CloudBridge/pull/115)).
- OAuth token refreshing ([#117](https://github.com/jacobras/CloudBridge/pull/117)).
- Support for multiple Google Drive accounts.
- Progress callback for uploads.
- Default timeouts.

### Changed

- **Breaking:** renamed `CloudServiceException` to `CloudBridgeException`.
- Throw `SetupException` if Google Drive auth fails because the app is not registered correctly.
- Strip weak etag prefix and surrounding quotes from etags
  ([#110](https://github.com/jacobras/CloudBridge/pull/110)).

### Fixed

- Google Drive not getting a refresh token after access had been granted before.
- `LocalAuthenticationServer` hanging, and not waiting for server shutdown before restarting.
- Dropbox token refresh request not being sent as form URL encoded.
- Debug logging slowing down requests a lot.

## [0.10.0] - 2026-10-26

### Added

- More file upload, rename and fetch APIs
  ([#106](https://github.com/jacobras/CloudBridge/pull/106)).

## [0.9.0] - 2026-10-22

### Added

- Delta calls ([#105](https://github.com/jacobras/CloudBridge/pull/105)).

## [0.8.0] - 2026-08-21

### Added

- WebDAV support ([#95](https://github.com/jacobras/CloudBridge/pull/95)).

## [0.7.0] - 2026-07-25

### Added

- Google Drive support on iOS ([#87](https://github.com/jacobras/CloudBridge/pull/87)).

## [0.6.0] - 2026-07-22

### Added

- Android support ([#64](https://github.com/jacobras/CloudBridge/pull/64)).
- iOS support ([#77](https://github.com/jacobras/CloudBridge/pull/77)).

## [0.5.0] - 2026-06-06

### Added

- Desktop support ([#56](https://github.com/jacobras/CloudBridge/pull/56)).

## [0.4.0] - 2025-10-18

### Added

- Get user info (name and email address) ([#21](https://github.com/jacobras/CloudBridge/pull/21)).

## [0.3.2] - 2025-10-15

### Fixed

- Default value for Dropbox `expires_in` field.

## [0.3.1] - 2025-10-15

### Added

- Support for Dropbox legacy long-lived access tokens.

### Changed

- `Id`'s value is now public.

## [0.3.0] - 2025-10-11

### Added

- `path` of files and folders, and `updateFile()` and `delete()` APIs
  ([#15](https://github.com/jacobras/CloudBridge/pull/15)).

## [0.2.0] - 2025-10-10

### Added

- Read modified time of a file ([#14](https://github.com/jacobras/CloudBridge/pull/14)).

## [0.1.1] - 2025-10-09

### Fixed

- Google Drive implicit flow ([#13](https://github.com/jacobras/CloudBridge/pull/13)).

## [0.1.0] - 2025-10-08

### Added

- Create and list folders ([#9](https://github.com/jacobras/CloudBridge/pull/9)).

## [0.0.1] - 2025-10-08

### Added

- Initial release with Dropbox, Google Drive and OneDrive support for web: writing files, getting
  file details
  and downloading file content.

[Unreleased]: https://github.com/jacobras/CloudBridge/compare/0.15.0...HEAD
[0.15.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.15.0
[0.14.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.14.0
[0.13.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.13.0
[0.12.1]: https://github.com/jacobras/CloudBridge/releases/tag/0.12.1
[0.12.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.12.0
[0.11.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.11.0
[0.10.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.10.0
[0.9.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.9.0
[0.8.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.8.0
[0.7.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.7.0
[0.6.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.6.0
[0.5.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.5.0
[0.4.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.4.0
[0.3.2]: https://github.com/jacobras/CloudBridge/releases/tag/0.3.2
[0.3.1]: https://github.com/jacobras/CloudBridge/releases/tag/0.3.1
[0.3.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.3.0
[0.2.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.2.0
[0.1.1]: https://github.com/jacobras/CloudBridge/releases/tag/0.1.1
[0.1.0]: https://github.com/jacobras/CloudBridge/releases/tag/0.1.0
[0.0.1]: https://github.com/jacobras/CloudBridge/releases/tag/0.0.1
