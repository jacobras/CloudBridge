package nl.jacobras.cloudbridge.provider.onedrive

import de.jensklingenberg.ktorfit.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Docs: https://learn.microsoft.com/en-us/onedrive/developer/rest-api/api/driveitem_put_content
 */
internal interface OneDriveApi {

    @POST("https://login.microsoftonline.com/common/oauth2/v2.0/token")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    @FormUrlEncoded
    suspend fun getToken(
        @Field("client_id") clientId: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): TokenResponse

    @GET("v1.0/me/drive/special/approot/children")
    suspend fun listFiles(): FileResponse

    @POST("v1.0/me/drive/special/approot/children")
    @Headers("Content-Type: application/json")
    suspend fun createFolder(@Body content: String)

    @GET("v1.0/me/drive/special/approot:/{path}:/content")
    suspend fun downloadFile(
        @Path("path") path: String
    ): String

    @PUT("v1.0/me/drive/special/approot:/{path}:/content")
    @Headers("Content-Type: text/plain")
    suspend fun uploadFile(
        @Path("path") path: String,
        @Body content: String
    )
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

    @SerialName("value")
    val files: List<FileEntry>
)

@Serializable
internal data class FileEntry(

    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("size")
    val size: Long? = 0,

    @SerialName("lastModifiedDateTime")
    val lastModified: String,

    @SerialName("folder")
    val folder: Folder? = null
)

@Serializable
internal data class CreateFolderArg(

    @SerialName("name")
    val name: String,

    @SerialName("folder")
    val folder: Folder = Folder(0)
)

@Serializable
internal data class Folder(

    @SerialName("childCount")
    val childCount: Int
)