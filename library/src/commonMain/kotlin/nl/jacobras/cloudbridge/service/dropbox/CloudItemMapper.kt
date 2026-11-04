package nl.jacobras.cloudbridge.service.dropbox

import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import kotlin.time.Instant

internal fun FileEntry.toCloudItem(): CloudItem {
    return when (tag) {
        "file" -> {
            CloudFile(
                id = CloudItemId(id ?: error("Missing id for file")),
                path = pathDisplay.asFilePath(),
                name = name,
                sizeInBytes = size ?: error("Missing size for file"),
                modified = Instant.parse(
                    clientModified ?: error("Missing modified time for file")
                ),
                revision = rev ?: error("Missing rev for file")
            )
        }
        "folder" -> {
            CloudFolder(
                id = CloudItemId(id ?: error("Missing id for folder")),
                path = pathDisplay.asFolderPath(),
                name = name
            )
        }
        else -> error("Unsupported tag: $tag")
    }
}
