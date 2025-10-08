package nl.jacobras.cloudbridge.model

/**
 * Represents a file in the cloud.
 *
 * @param id The unique identifier of the file.
 * @param name The name of the file, including extension.
 */
public data class CloudFile(
    override val id: String,
    override val name: String,
    val sizeInBytes: Long
) : CloudItem