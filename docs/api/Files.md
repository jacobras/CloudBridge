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
} catch (e: CloudServiceException) {
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
} catch (e: CloudServiceException) {
    // Handle...
}
```

## Creating a folder

```kotlin
try {
    service.createFolder("/Projects".asFolderPath())
} catch (e: CloudServiceException) {
    // Handle...
}
```

## Creating a text file

```kotlin
try {
    val file: CloudFile = service.createFile("/Projects/Welcome.txt".asFilePath(), "Hello world!")
    println("Created ${file.id} at revision ${file.revision}")
} catch (e: CloudServiceException) {
    // Handle...
}
```

## Updating a text file

```kotlin
try {
    service.updateFile(fileId, "Updated content")
} catch (e: CloudServiceException) {
    // Handle...
}
```

## Uploading binary or large files

For images, archives and other binary content, pass a `kotlinx.io.Source`. The content is read until
its end, but the stream isn't closed. Files larger than 10 MB[^1] are automatically uploaded in
chunks.

```kotlin
try {
    val localPath = Path("photo.jpg")
    SystemFileSystem.source(localPath).buffered().use { source ->
        service.createFile(
            path = localPath.name.asFilePath(),
            source = source,
            sizeInBytes = SystemFileSystem.metadataOrNull(localPath)?.size,
            mimeType = "image/jpeg"
        )
    }
} catch (e: CloudServiceException) {
    // Handle...
}
```

* `sizeInBytes` is optional, except for OneDrive files larger than 10 MB (throws an
  `IllegalArgumentException` if missing in that case).
* `mimeType` defaults to `application/octet-stream`. Dropbox doesn't store MIME types and ignores
  it.
* WebDAV has no chunked uploads, so the content is streamed in a single request.
* `updateFile()` has as similar signature as `createFile()`.

### Using an `InputStream`

Here's an example on how to use an `InputStream` on Android and desktop:

```kotlin
try {
    val file = File("photo.jpg")
    file.inputStream().use { stream ->
        service.createFile(
            path = file.name.asFilePath(),
            source = stream.asSource().buffered(),
            sizeInBytes = file.length(),
            mimeType = "image/jpeg"
        )
    }
} catch (e: CloudServiceException) {
  // Handle...
}
```

## Downloading a file

```kotlin
try {
    val content = service.downloadFile(fileId)
} catch (e: CloudServiceException) {
    // Handle...
}
```

## Deleting a file or folder

```kotlin
try {
    service.delete(itemId)
} catch (e: CloudServiceException) {
    // Handle...
}
```

[^1]: 10 MB is "optimal for stable high speed connections" and supported by all services.
(<https://learn.microsoft.com/en-us/graph/api/driveitem-createuploadsession?view=graph-rest-1.0#best-practices)>.