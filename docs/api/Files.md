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

## Listing changes (delta)

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

## Creating a file

```kotlin
try {
    val file: CloudFile = service.createFile("/Projects/Welcome.txt".asFilePath(), "Hello world!")
    println("Created ${file.id} at revision ${file.revision}")
} catch (e: CloudServiceException) {
    // Handle...
}
```

## Updating a file

```kotlin
try {
    service.updateFile(fileId, "Updated content")
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