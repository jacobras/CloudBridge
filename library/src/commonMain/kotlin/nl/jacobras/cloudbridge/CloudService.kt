package nl.jacobras.cloudbridge

import nl.jacobras.cloudbridge.auth.CloudAuthenticator
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.FilePath
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.Id

public interface CloudService {

    /**
     * Returns `true` if the account is authenticated.
     */
    public fun isAuthenticated(): Boolean

    /**
     * Returns a [CloudAuthenticator] that can be used to authorize a user.
     *
     * @param redirectUri URI to redirect user to upon successful authorization.
     * Needs to be registered in the service's developer console.
     */
    public fun getAuthenticator(
        redirectUri: String
    ): CloudAuthenticator

    /**
     * Clears all tokens for this service.
     */
    public fun logout()

    /**
     * Lists all files and folders.
     *
     * @throws CloudServiceException
     */
    public suspend fun listFiles(path: FolderPath): List<CloudItem>

    /**
     * Creates a folder at [path].
     *
     * @throws CloudServiceException
     */
    public suspend fun createFolder(path: FolderPath)

    /**
     * Creates a file at path [path] with text content [content].
     *
     * @throws CloudServiceException
     */
    public suspend fun createFile(path: FilePath, content: String)

    /**
     * Retrieves the file with [id].
     *
     * @throws CloudServiceException
     */
    public suspend fun downloadFile(id: Id): String


    /**
     * Deletes the file/folder with [id].
     *
     * @throws CloudServiceException
     */
    public suspend fun delete(id: Id)
}