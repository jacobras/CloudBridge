package nl.jacobras.cloudbridge.provider.dropbox

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

    @POST("2/files/list_folder")
    @Headers("Content-Type: application/json")
    suspend fun listFiles(
        @Body data: String = "{\"include_deleted\": false,\"include_has_explicit_shared_members\": false,\"include_media_info\": false,\"include_mounted_folders\": true,\"include_non_downloadable_files\": true,\"path\": \"\",\"recursive\": false}"
    ): FileResponse

    @POST("https://content.dropboxapi.com/2/files/upload")
    @Headers("Content-Type: application/octet-stream")
    suspend fun uploadFile(
        @Header("Dropbox-API-Arg") arguments: String,
        @Body content: ByteArray
    ): String
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

    @SerialName("entries")
    val entries: List<FileEntry>,

    @SerialName("cursor")
    val cursor: String,

    @SerialName("has_more")
    val hasMore: Boolean
)

@Serializable
internal data class FileEntry(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("path_lower")
    val pathLower: String,

    @SerialName("path_display")
    val pathDisplay: String,

    @SerialName("size")
    val size: Long
)

@Serializable
internal data class DropboxUploadArg(
    @SerialName("path")
    val path: String,

    @SerialName("mode")
    val mode: String = "add",

    @SerialName("autorename")
    val autoRename: Boolean = true,

    @SerialName("mute")
    val mute: Boolean = false
)