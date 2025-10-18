package nl.jacobras.cloudbridge.service.dropbox

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Docs: https://www.dropbox.com/developers/documentation/http/documentation
 */
internal interface DropboxApi {

    @POST("oauth2/token")
    suspend fun getToken(
        @Query("client_id") clientId: String,
        @Query("code") code: String,
        @Query("grant_type") grantType: String = "authorization_code",
        @Query("redirect_uri") redirectUri: String,
        @Query("code_verifier") codeVerifier: String,
        @Query("scope") scope: String = "files.metadata.write files.content.write files.content.read"
    ): TokenResponse

    @POST("2/users/get_current_account")
    suspend fun getUserInfo(): DropboxUserInfo

    @POST("2/files/list_folder")
    @Headers("Content-Type: application/json")
    suspend fun listFiles(
        @Body arg: String
    ): FileResponse

    @POST("2/files/create_folder_v2")
    @Headers("Content-Type: application/json")
    suspend fun createFolder(@Body content: String)

    @POST("https://content.dropboxapi.com/2/files/download")
    suspend fun downloadFile(
        @Header("Dropbox-API-Arg") arguments: String,
    ): String

    @POST("https://content.dropboxapi.com/2/files/upload")
    @Headers("Content-Type: application/octet-stream")
    suspend fun uploadFile(
        @Header("Dropbox-API-Arg") arguments: String,
        @Body content: ByteArray
    ): String

    @POST("2/files/delete_v2")
    @Headers("Content-Type: application/json")
    suspend fun deleteByPath(@Body body: DeleteRequest)
}

@Serializable
internal data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("expires_in")
    val expiresInSeconds: Int? = null // Will be null in case of legacy long-lived access token
)

@Serializable
internal data class DropboxUserInfo(
    @SerialName("name")
    val name: DropboxUserInfoName? = null,

    @SerialName("email")
    val emailAddress: String? = null
)

@Serializable
internal data class DropboxUserInfoName(
    @SerialName("display_name")
    val displayName: String
)

@Serializable
internal data class FileResponse(

    @SerialName("entries")
    val entries: List<FileEntry>,

    @SerialName("cursor")
    val cursor: String,

    @SerialName("has_more")
    val hasMore: Boolean
)

@Serializable
internal data class FileEntry(

    /**
     * "file" or "folder".
     */
    @SerialName(".tag")
    val tag: String,

    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("path_lower")
    val pathLower: String,

    @SerialName("path_display")
    val pathDisplay: String,

    @SerialName("size")
    val size: Long? = null,

    @SerialName("client_modified")
    val clientModified: String? = null
)

@Serializable
internal data class ListFolderArg(
    @SerialName("path")
    val path: String,

    @SerialName("include_deleted")
    val includeDeleted: Boolean = false,

    @SerialName("recursive")
    val recursive: Boolean = false
)

@Serializable
internal data class DropboxUploadArg(
    @SerialName("path")
    val path: String,

    @SerialName("mode")
    val mode: Mode,

    @SerialName("autorename")
    val autoRename: Boolean = true,

    @SerialName("mute")
    val mute: Boolean = false
)

@Serializable
internal enum class Mode {
    @SerialName("add")
    Add,

    @SerialName("overwrite")
    Overwrite,

    @SerialName("update")
    Update
}

@Serializable
internal data class DropboxDownloadArg(
    @SerialName("path")
    val path: String
)

@Serializable
internal data class CreateFolderRequest(
    @SerialName("path")
    val path: String
)

@Serializable
internal data class DeleteRequest(
    @SerialName("path")
    val path: String
)