package nl.jacobras.cloudbridge

import kotlinx.io.Source
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FilePath
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.UserInfo

public interface CloudService {

    /**
     * Returns `true` if the account is authenticated.
     */
    public fun isAuthenticated(): Boolean

    /**
     * Fetches the user's info (like name and email address).
     *
     * @throws CloudServiceException
     */
    public suspend fun getUserInfo(): UserInfo

    /**
     * Lists all files and folders, one level deep. Does not fetch nested content.
     *
     * @throws CloudServiceException
     */
    public suspend fun listFiles(path: FolderPath): List<CloudItem>

    /**
     * Creates a folder at [path].
     *
     * @return The created folder.
     * @throws CloudServiceException
     */
    public suspend fun createFolder(path: FolderPath): CloudFolder

    /**
     * Creates a file at path [path] with the text [content].
     *
     * @return The created file.
     * @throws CloudServiceException
     */
    public suspend fun createFile(path: FilePath, content: String): CloudFile

    /**
     * Updates the file with id [id] to the text [content].
     *
     * @return The updated file.
     * @throws CloudServiceException
     */
    public suspend fun updateFile(id: CloudItemId, content: String): CloudFile

    /**
     * Creates a file at path [path] with the binary content of [source]. The source is read
     * until its end, but not closed. Large files are automatically uploaded in chunks.
     *
     * @param sizeInBytes Size of the content, only required for OneDrive if > 10 MB.
     * @param mimeType MIME type of the content. Not used by Dropbox.
     * @return The created file.
     * @throws IllegalArgumentException when uploading >10 MB to OneDrive and [sizeInBytes] is missing.
     * @throws CloudServiceException
     */
    public suspend fun createFile(
        path: FilePath,
        source: Source,
        sizeInBytes: Long? = null,
        mimeType: String = "application/octet-stream"
    ): CloudFile

    /**
     * Updates the file with id [id] to the binary content of [source]. The source is read
     * until its end, but not closed. Large files are automatically uploaded in chunks.
     *
     * @param sizeInBytes Size of the content, only required for OneDrive if > 10 MB.
     * @param mimeType MIME type of the content. Not used by Dropbox.
     * @return The updated file.
     * @throws IllegalArgumentException when uploading >10 MB to OneDrive and [sizeInBytes] is missing.
     * @throws CloudServiceException
     */
    public suspend fun updateFile(
        id: CloudItemId,
        source: Source,
        sizeInBytes: Long? = null,
        mimeType: String = "application/octet-stream"
    ): CloudFile

    /**
     * Retrieves the file with [id].
     *
     * @throws CloudServiceException
     */
    public suspend fun downloadFile(id: CloudItemId): String


    /**
     * Deletes the file/folder with [id].
     *
     * @throws CloudServiceException
     */
    public suspend fun delete(id: CloudItemId)
}