# Files

Files and folders are addressed with `FilePath` and `FolderPath`, both of which always start with a
slash. Create them with the `asFilePath()` / `asFolderPath()` extension functions:

```kotlin
val file = "/Projects/welcome.txt".asFilePath()
val folder = "/Projects".asFolderPath()
val root = "/".asFolderPath()
```

## Listing files

Lists all files and folders one level deep; nested content is not fetched.

```kotlin
try {
    val items = service.listFiles("/".asFolderPath())
} catch (e: CloudBridgeException) {
    // Handle...
}
```

## Listing delta changes

Lists all file/folder changes since the last time this was called. Not supported by all services,
see [Cloud services](../services/Overview.md) for the support matrix.

```kotlin
try {
    val result = service.getDelta(previousCursor)
    val previouCursor = result.newCursor
    val changes = result.changes
} catch (e: CloudBridgeException) {
    // Handle...
}
```

## Getting file or folder info

Fetches the metadata of a single file or folder. If nothing exists at the path, or the item has the
other type (a folder when asking for a file or the other way round), a `NotFoundException` is
thrown.

```kotlin
try {
    val file: CloudFile = service.getFileInfo("/Projects/Welcome.txt".asFilePath())
    val folder: CloudFolder = service.getFolderInfo("/Projects".asFolderPath())
} catch (e: CloudBridgeException) {
    // Handle...
}
```

Calling `getFolderInfo()` for the root folder (`/`) throws an `IllegalArgumentException`.

## Creating a folder

```kotlin
try {
    service.createFolder("/Projects".asFolderPath())
} catch (e: CloudBridgeException) {
    // Handle...
}
```

## Creating a text file

```kotlin
try {
    val file: CloudFile = service.createFile("/Projects/Welcome.txt".asFilePath(), "Hello world!")
    println("Created ${file.id} at revision ${file.revision}")
} catch (e: CloudBridgeException) {
    // Handle...
}
```

## Updating a text file

```kotlin
try {
    val file: CloudFile = service.updateFile(fileId, "Updated content")
    println("Updated ${file.id} to revision ${file.revision}")
} catch (e: CloudBridgeException) {
    // Handle...
}
```

## Uploading binary or large files

For images, archives and other binary content, pass a function that opens a `kotlinx.io.Source`.
Files larger than 10 MB are automatically uploaded in chunks[^1].

```kotlin
try {
    val localPath = Path("photo.jpg")
    service.createFile(
        path = localPath.name.asFilePath(),
        source = { SystemFileSystem.source(localPath).buffered() },
        sizeInBytes = SystemFileSystem.metadataOrNull(localPath)?.size,
        mimeType = "image/jpeg",
        onProgress = { bytesSent, totalBytes -> println("Sent $bytesSent of $totalBytes bytes") }
    )
} catch (e: CloudBridgeException) {
    // Handle...
}
```

* The function is called once per attempt, so an upload can be [retried](RateLimiting.md#retries)
  after rate limiting. It must return a **new** `Source` at the start of the content each time. A
  `CloudBridgeException.SourceReadException` is thrown if it returns a source that was already
  read.
* Each source is read until its end and then closed by the library.
* `sizeInBytes` is optional, except for OneDrive files larger than 10 MB (throws a
  `CloudBridgeException.IllegalArgumentException` if missing or not matching the content size in
  that case).
* `mimeType` defaults to `application/octet-stream`. Dropbox doesn't store MIME types and ignores
  it.
* `onProgress` is optional and useful to track how far uploads are progressing.
  `totalBytes` is `sizeInBytes` if given, otherwise only known after the last chunk.
* WebDAV has no chunked uploads, so the content is streamed in a single request and `onProgress`
  is called once, when done.
* `updateFile()` has as similar signature as `createFile()`.

### Using an `InputStream`

Here's an example on how to use an `InputStream` on Android and desktop:

```kotlin
try {
    val file = File("photo.jpg")
    service.createFile(
        path = file.name.asFilePath(),
        source = { file.inputStream().asSource().buffered() },
        sizeInBytes = file.length(),
        mimeType = "image/jpeg"
    )
} catch (e: CloudBridgeException) {
    // Handle...
}
```

## Downloading a file

```kotlin
try {
    val content: String = service.downloadFile(fileId)
} catch (e: CloudBridgeException) {
    // Handle...
}
```

### Downloading binary or large files

For images, archives and other binary content, pass a `kotlinx.io.Sink`. The content is streamed
into it, so large files aren't held in memory. The sink is flushed, but not closed.

```kotlin
try {
    SystemFileSystem.sink(Path("photo.jpg")).buffered().use { sink ->
        service.downloadFile(fileId, sink)
    }
} catch (e: CloudBridgeException) {
    // Handle...
}
```

To get the content in memory instead, use a `Buffer`:

```kotlin
val buffer = Buffer()
service.downloadFile(fileId, buffer)
val bytes = buffer.readByteArray()
```

## Moving or renaming a file or folder

To rename an item, move it within the same folder. If an item already exists at the new path, a
`CloudBridgeException.ConflictException` is thrown.

```kotlin
try {
    // Rename/move a file
    service.moveFile(fileId, "/Projects/New name.txt".asFilePath())

    // Or a folder
    service.moveFolder(folderId, "/Projects2".asFolderPath())
} catch (e: CloudBridgeException) {
    // Handle...
}
```

On WebDAV, the id of an item is its path, so the returned item has a new id.

## Deleting a file or folder

```kotlin
try {
    service.delete(itemId)
} catch (e: CloudBridgeException) {
    // Handle...
}
```

[^1]: Chunk size is around 10 MB, which is "optimal for stable high speed connections"
(<https://learn.microsoft.com/en-us/graph/api/driveitem-createuploadsession?view=graph-rest-1.0#best-practices)>.
See the [support matrix](../services/Overview.md) for details.