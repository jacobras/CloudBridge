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
    service.createFile("/Projects/Welcome.txt".asFilePath(), "Hello world!")
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