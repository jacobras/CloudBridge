package nl.jacobras.cloudbridge.model

/**
 * Represents a folder in the cloud.
 *
 * @param id The unique identifier of the folder.
 * @param path The path of the item, including [name]. Note: for Google Drive specifically,
 * this contains IDs as parents. Please see library documentation.
 * @param name The name of the folder.
 */
public data class CloudFolder(
    override val id: String,
    override val path: FolderPath,
    override val name: String
) : CloudItem