package nl.jacobras.cloudbridge.providers.onedrive

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
}

@Serializable
internal data class TokenRequest(
    @SerialName("client_id")
    val clientId: String,

    @SerialName("redirect_uri")
    val redirectUri: String,

    @SerialName("code")
    val code: String,

    @SerialName("grant_type")
    val grantType: String = "authorization_code",

    @SerialName("code_verifier")
    val codeVerifier: String,
)

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
    @SerialName("name")
    val name: String,

    @SerialName("size")
    val size: Long
)