package nl.jacobras.cloudbridge.model

/**
 * Represents a folder in the cloud.
 *
 * @param id The unique identifier of the folder.
 * @param path The path of the item, including [name].
 * Beware of specific behaviour with Google Drive, see docs!
 * @param name The name of the folder.
 */
public data class CloudFolder(
    override val id: String,
    override val path: DirectoryPath,
    override val name: String
) : CloudItem