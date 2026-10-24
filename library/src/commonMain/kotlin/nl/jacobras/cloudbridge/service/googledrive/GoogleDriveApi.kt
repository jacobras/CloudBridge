package nl.jacobras.cloudbridge.service.googledrive

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.Multipart
import de.jensklingenberg.ktorfit.http.PATCH
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import de.jensklingenberg.ktorfit.http.Streaming
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.statement.HttpStatement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.jacobras.cloudbridge.auth.TokenResponse

/**
 * Docs: https://developers.google.com/workspace/drive/api/reference/rest/v3/files
 */
internal interface GoogleDriveApi {

    @POST("https://oauth2.googleapis.com/token")
    @FormUrlEncoded
    suspend fun getToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String?,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): TokenResponse

    @GET("drive/v3/about?fields=user")
    suspend fun getUserInfo(): UserResponse

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Query("q") query: String?,
        @Query("pageToken") pageToken: String? = null,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("fields") fields: String = "nextPageToken,files($FILE_FIELDS)",
        @Query("pageSize") pageSize: Int = 100
    ): FileResponse

    @GET("drive/v3/files/appDataFolder?fields=id")
    suspend fun getAppDataFolder(): DriveFileId

    @GET("drive/v3/changes/startPageToken")
    suspend fun getStartPageToken(): StartPageTokenResponse

    @GET("drive/v3/changes")
    suspend fun listChanges(
        @Query("pageToken") pageToken: String,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("fields") fields: String = "nextPageToken,newStartPageToken,changes(fileId,removed,file($FILE_FIELDS))",
        @Query("pageSize") pageSize: Int = 100
    ): ChangesResponse

    @POST("drive/v3/files")
    suspend fun createFolder(
        @Body content: String,
        @Query("fields") fields: String = FILE_FIELDS
    ): DriveFile

    @POST("upload/drive/v3/files?uploadType=multipart")
    @Multipart
    suspend fun createFile(
        @Body map: MultiPartFormDataContent,
        @Query("fields") fields: String = FILE_FIELDS
    ): DriveFile

    @PATCH("upload/drive/v3/files/{fileId}?uploadType=media")
    suspend fun updateFile(
        @Path("fileId") id: String,
        @Header("Content-Type") contentType: String,
        @Body content: ByteArray,
        @Query("fields") fields: String = FILE_FIELDS
    ): DriveFile

    @GET("drive/v3/files/{fileId}")
    suspend fun getFile(
        @Path("fileId") id: String,
        @Query("fields") fields: String = FILE_FIELDS
    ): DriveFile

    @PATCH("drive/v3/files/{fileId}")
    @Headers("Content-Type: application/json")
    suspend fun move(
        @Path("fileId") id: String,
        @Body content: String,
        @Query("addParents") addParents: String? = null,
        @Query("removeParents") removeParents: String? = null,
        @Query("fields") fields: String = FILE_FIELDS
    ): DriveFile

    @GET("drive/v3/files/{fileId}?alt=media")
    suspend fun downloadFile(@Path("fileId") id: String): ByteArray

    @Streaming
    @GET("drive/v3/files/{fileId}?alt=media")
    suspend fun downloadFileStreaming(@Path("fileId") id: String): HttpStatement

    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteById(@Path("fileId") fileId: String)
}

@Serializable
internal data class UserResponse(
    @SerialName("user")
    val user: UserInfo?
)

@Serializable
internal data class UserInfo(
    @SerialName("displayName")
    val name: String? = null,

    @SerialName("emailAddress")
    val emailAddress: String? = null
)

@Serializable
internal data class FileResponse(
    @SerialName("files")
    val files: List<DriveFile>,

    @SerialName("nextPageToken")
    val nextPageToken: String? = null
)

@Serializable
internal data class DriveFileId(
    @SerialName("id")
    val id: String
)

@Serializable
internal data class StartPageTokenResponse(
    @SerialName("startPageToken")
    val startPageToken: String
)

@Serializable
internal data class ChangesResponse(
    @SerialName("changes")
    val changes: List<DriveChange>,

    /**
     * Only present if there are more pages.
     */
    @SerialName("nextPageToken")
    val nextPageToken: String? = null,

    /**
     * Only present on the last page.
     */
    @SerialName("newStartPageToken")
    val newStartPageToken: String? = null
)

@Serializable
internal data class DriveChange(
    @SerialName("fileId")
    val fileId: String,

    @SerialName("removed")
    val removed: Boolean = false,

    /**
     * Absent when [removed] is `true`.
     */
    @SerialName("file")
    val file: DriveFile? = null
)

@Serializable
internal data class DriveFile(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("mimeType")
    val mimeType: String,

    @SerialName("size")
    val size: String? = null,

    @SerialName("modifiedTime")
    val modified: String,

    @SerialName("parents")
    val parents: List<String> = emptyList(),

    @SerialName("version")
    val version: String? = null,

    @SerialName("trashed")
    val trashed: Boolean = false
)

@Serializable
internal data class DriveFileMetadata(
    @SerialName("name")
    val name: String,

    @SerialName("mimeType")
    val mimeType: String,

    @SerialName("parents")
    val parents: List<String>
)

@Serializable
internal data class RenameRequest(
    @SerialName("name")
    val name: String
)

@Serializable
internal data class CreateFolderRequest(
    @SerialName("name")
    val name: String,

    @SerialName("mimeType")
    val mimeType: String = "application/vnd.google-apps.folder",

    @SerialName("parents")
    val parents: List<String>
)

internal const val FILE_FIELDS = "id,name,mimeType,size,modifiedTime,parents,version,trashed"