package nl.jacobras.cloudbridge.service.googledrive

import de.jensklingenberg.ktorfit.http.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Docs: https://developers.google.com/workspace/drive/api/reference/rest/v3/files
 */
internal interface GoogleDriveApi {

    @POST("https://oauth2.googleapis.com/token")
    @FormUrlEncoded
    suspend fun getToken(
        @Field("client_id") clientId: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): TokenResponse

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Query("q") query: String,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("fields") fields: String = "files(id,name,mimeType,size,modifiedTime,parents)",
        @Query("pageSize") pageSize: Int = 100
    ): FileResponse

    @POST("drive/v3/files")
    suspend fun createFolder(@Body content: String)

    @POST("upload/drive/v3/files?uploadType=multipart")
    @Multipart
    suspend fun uploadFile(@Body map: MultiPartFormDataContent)

    @GET("drive/v3/files/{fileId}?alt=media")
    suspend fun downloadFile(@Path("fileId") id: String): ByteArray

    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteById(@Path("fileId") fileId: String)
}

@Serializable
internal data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("expires_in")
    val expiresInSeconds: Int
)

@Serializable
internal data class FileResponse(
    @SerialName("files")
    val files: List<DriveFile>
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
    val parents: List<String>
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
internal data class CreateFolderRequest(
    @SerialName("name")
    val name: String,

    @SerialName("mimeType")
    val mimeType: String = "application/vnd.google-apps.folder",

    @SerialName("parents")
    val parents: List<String>
)