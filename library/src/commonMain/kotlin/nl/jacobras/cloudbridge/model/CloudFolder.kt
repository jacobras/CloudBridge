package nl.jacobras.cloudbridge.model

/**
 * Represents a folder in the cloud.
 *
 * @param id The unique identifier of the folder.
 * @param name The name of the folder.
 */
public data class CloudFolder(
    override val id: String,
    override val name: String
) : CloudItem