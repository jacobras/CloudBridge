package nl.jacobras.cloudbridge.model

/**
 * Represents an item in the cloud. Could be either a [CloudFile] or a [CloudFolder].
 *
 * @param id The unique identifier of the item.
 * @param path The path of the item, including [name]. Note: for Google Drive specifically,
 * this contains IDs as parents. Please see library documentation.
 * @param name The name of the item (filename and extension in case of file).
 */
public sealed interface CloudItem {
    public val id: String
    public val path: Path
    public val name: String
}