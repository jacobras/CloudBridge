package nl.jacobras.cloudbridge.model

import kotlin.time.Instant

/**
 * Represents a file in the cloud.
 *
 * @param id Unique identifier of the file.
 * @param path The path of the item, including [name].
 * Beware of specific behaviour with Google Drive, see docs!
 * @param name Name of the file, including extension.
 * @param sizeInBytes Size of the file in bytes.
 * @param modified Last time the file was modified.
 */
public data class CloudFile(
    override val id: String,
    override val path: FilePath,
    override val name: String,
    val sizeInBytes: Long,
    val modified: Instant
) : CloudItem