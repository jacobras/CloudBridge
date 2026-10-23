package nl.jacobras.cloudbridge.service.dropbox

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.jacobras.cloudbridge.auth.TokenResponse

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

    @POST("2/files/list_folder/continue")
    @Headers("Content-Type: application/json")
    suspend fun listFilesContinue(
        @Body arg: String
    ): FileResponse

    @POST("2/files/create_folder_v2")
    @Headers("Content-Type: application/json")
    suspend fun createFolder(@Body content: String): CreateFolderResponse

    @POST("https://content.dropboxapi.com/2/files/download")
    suspend fun downloadFile(
        @Header("Dropbox-API-Arg") arguments: String,
    ): String

    @POST("https://content.dropboxapi.com/2/files/upload")
    @Headers("Content-Type: application/octet-stream")
    suspend fun uploadFile(
        @Header("Dropbox-API-Arg") arguments: String,
        @Body content: ByteArray
    ): UploadResponse

    @POST("https://content.dropboxapi.com/2/files/upload_session/start")
    @Headers("Content-Type: application/octet-stream")
    suspend fun uploadSessionStart(
        @Header("Dropbox-API-Arg") arguments: String,
        @Body content: ByteArray
    ): UploadSessionStartResponse

    @POST("https://content.dropboxapi.com/2/files/upload_session/append_v2")
    @Headers("Content-Type: application/octet-stream")
    suspend fun uploadSessionAppend(
        @Header("Dropbox-API-Arg") arguments: String,
        @Body content: ByteArray
    )

    @POST("https://content.dropboxapi.com/2/files/upload_session/finish")
    @Headers("Content-Type: application/octet-stream")
    suspend fun uploadSessionFinish(
        @Header("Dropbox-API-Arg") arguments: String,
        @Body content: ByteArray
    ): UploadResponse

    @POST("2/files/delete_v2")
    @Headers("Content-Type: application/json")
    suspend fun deleteByPath(@Body body: DeleteRequest)
}

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
     * "file", "folder" or "deleted".
     */
    @SerialName(".tag")
    val tag: String,

    /**
     * Absent for "deleted" entries.
     */
    @SerialName("id")
    val id: String? = null,

    @SerialName("name")
    val name: String,

    @SerialName("path_lower")
    val pathLower: String,

    @SerialName("path_display")
    val pathDisplay: String,

    @SerialName("size")
    val size: Long? = null,

    @SerialName("client_modified")
    val clientModified: String? = null,

    @SerialName("rev")
    val rev: String? = null
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
internal data class ListFolderContinueArg(
    @SerialName("cursor")
    val cursor: String
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
internal data class UploadSessionStartArg(
    @SerialName("close")
    val close: Boolean = false
)

@Serializable
internal data class UploadSessionStartResponse(
    @SerialName("session_id")
    val sessionId: String
)

@Serializable
internal data class UploadSessionCursor(
    @SerialName("session_id")
    val sessionId: String,

    /**
     * Amount of bytes uploaded so far.
     */
    @SerialName("offset")
    val offset: Long
)

@Serializable
internal data class UploadSessionAppendArg(
    @SerialName("cursor")
    val cursor: UploadSessionCursor
)

@Serializable
internal data class UploadSessionFinishArg(
    @SerialName("cursor")
    val cursor: UploadSessionCursor,

    @SerialName("commit")
    val commit: DropboxUploadArg
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
internal data class CreateFolderResponse(
    @SerialName("metadata")
    val metadata: FolderMetadata
)

@Serializable
internal data class FolderMetadata(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("path_lower")
    val pathLower: String
)

@Serializable
internal data class UploadResponse(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("path_lower")
    val pathLower: String,

    @SerialName("size")
    val size: Long,

    @SerialName("client_modified")
    val clientModified: String,

    @SerialName("rev")
    val rev: String
)

@Serializable
internal data class DeleteRequest(
    @SerialName("path")
    val path: String
)