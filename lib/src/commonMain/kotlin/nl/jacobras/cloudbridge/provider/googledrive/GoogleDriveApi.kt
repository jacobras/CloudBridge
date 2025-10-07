package nl.jacobras.cloudbridge.provider.googledrive

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Multipart
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
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
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("fields") fields: String = "files(id,name,mimeType)",
        @Query("pageSize") pageSize: Int = 100
    ): FileResponse

    @POST("upload/drive/v3/files?uploadType=multipart")
    @Multipart
    suspend fun uploadFile(
        @Body map: MultiPartFormDataContent
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