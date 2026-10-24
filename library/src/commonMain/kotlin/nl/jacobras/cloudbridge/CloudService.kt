package nl.jacobras.cloudbridge

import kotlinx.io.Sink
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
     * Fetches the metadata of the file at [path].
     *
     * @throws CloudServiceException.NotFoundException when there's no file at [path], or when
     * there's actually a folder at [path].
     * @throws CloudServiceException
     */
    public suspend fun getFileInfo(path: FilePath): CloudFile

    /**
     * Fetches the metadata of the folder at [path].
     *
     * @throws IllegalArgumentException when [path] is the root folder.
     * @throws CloudServiceException.NotFoundException when there's no folder at [path], or when
     * there's actually a file at [path].
     * @throws CloudServiceException
     */
    public suspend fun getFolderInfo(path: FolderPath): CloudFolder

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
     * @throws CloudServiceException.IllegalArgumentException when uploading >10 MB to OneDrive and
     * [sizeInBytes] is missing or doesn't match the content size.
     * @throws CloudServiceException.SourceReadException when reading [source] fails.
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
     * @throws CloudServiceException.IllegalArgumentException when uploading >10 MB to OneDrive and
     * [sizeInBytes] is missing or doesn't match the content size.
     * @throws CloudServiceException.SourceReadException when reading [source] fails.
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
     * Downloads the binary content of the file with [id] into [sink].
     *
     * The content is streamed, so large files aren't held in memory. The sink is flushed,
     * but not closed.
     *
     * @throws CloudServiceException.SinkWriteException when writing to [sink] fails.
     * @throws CloudServiceException
     */
    public suspend fun downloadFile(id: CloudItemId, sink: Sink)

    /**
     * Moves the file with [id] to [newPath]. To rename a file, move it within the same folder:
     * `moveFile(file.id, "/Notes/new name.txt".asFilePath())`.
     *
     * On WebDAV, the id is the path, so the returned file has a new id.
     *
     * @return The moved file.
     * @throws CloudServiceException.ConflictException when an item at [newPath] already exists.
     * @throws CloudServiceException
     */
    public suspend fun moveFile(id: CloudItemId, newPath: FilePath): CloudFile

    /**
     * Moves the folder with [id] to [newPath]. To rename a folder, move it within the same parent
     * folder: `moveFolder(folder.id, "/Notes/New name".asFolderPath())`.
     *
     * On WebDAV, the id is the path, so the returned folder has a new id.
     *
     * @return The moved folder.
     * @throws IllegalArgumentException when [newPath] is the root folder.
     * @throws CloudServiceException.ConflictException when an item at [newPath] already exists.
     * @throws CloudServiceException
     */
    public suspend fun moveFolder(id: CloudItemId, newPath: FolderPath): CloudFolder

    /**
     * Deletes the file/folder with [id].
     *
     * @throws CloudServiceException
     */
    public suspend fun delete(id: CloudItemId)
}