package nl.jacobras.cloudbridge

import nl.jacobras.cloudbridge.auth.CloudAuthenticator
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.FolderPath

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
    public suspend fun listFiles(): List<CloudItem>

    /**
     * Creates a folder at [path].
     *
     * @throws CloudServiceException
     */
    public suspend fun createFolder(path: FolderPath)

    /**
     * Creates a file with name [filename] and text content [content].
     *
     * @throws CloudServiceException
     */
    public suspend fun createFile(filename: String, content: String)

    public interface DownloadById {

        /**
         * Retrieves the file with [id].
         *
         * @throws CloudServiceException
         */
        public suspend fun downloadFileById(id: String): String
    }

    public interface DownloadByPath {

        /**
         * Retrieves the file at [path].
         *
         * @throws CloudServiceException
         */
        public suspend fun downloadFileByPath(path: String): String
    }
}