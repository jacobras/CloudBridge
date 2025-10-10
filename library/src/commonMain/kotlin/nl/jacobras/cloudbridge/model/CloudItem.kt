package nl.jacobras.cloudbridge.model

/**
 * Represents an item in the cloud. Could be either a [CloudFile] or a [CloudFolder].
 *
 * @param id The unique identifier of the item.
 * @param path The path of the item, including [name].
 * Beware of specific behaviour with Google Drive, see docs!
 * @param name The name of the item (filename and extension in case of file).
 */
public sealed interface CloudItem {
    public val id: String
    public val path: Path
    public val name: String
}