package nl.jacobras.cloudbridge.model

import kotlin.time.Instant

/**
 * Represents a file in the cloud.
 *
 * @param id Unique identifier of the file.
 * @param name Name of the file, including extension.
 * @param sizeInBytes Size of the file in bytes.
 * @param modified Last time the file was modified.
 */
public data class CloudFile(
    override val id: String,
    override val name: String,
    val sizeInBytes: Long,
    val modified: Instant
) : CloudItem