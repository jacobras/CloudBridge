# Vision

CloudBridge aims to make it easy to support multiple cloud services in your app, without having to
go learn every detail of each specific service.

## Privacy

The library only supports limited/private app folders, no full access.

## Paths and IDs

The library prefers to work with IDs over paths.

## Strongly typed

`id` and `path` variables are typed as much as possible, to prevent accidental mix-ups.

## Unified error handling

Dropbox will throw `409` when it can't find a path. Other services throw `404`. CloudBridge turns
them both into `CloudServiceException.NotFoundException.`

---

_Feel free to open an issue if you have a different use case for any of these._