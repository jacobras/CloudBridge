package nl.jacobras.cloudbridge.service.onedrive

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.jacobras.cloudbridge.auth.TokenResponse

/**
 * Docs: https://learn.microsoft.com/en-us/onedrive/developer/rest-api/resources/driveitem
 *
 * Note: paths must be surrounded by a :double colon: in the request URL.
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

    @GET("oidc/userinfo")
    suspend fun getUserInfo(): OneDriveUserInfo

    @GET("v1.0/me/drive/special/approot/children")
    suspend fun listFiles(): FileResponse

    @GET("v1.0/me/drive/special/approot:/{folderPath}:/children")
    suspend fun listFiles(
        @Path("folderPath", encoded = true) folderPath: String
    ): FileResponse

    @POST("v1.0/me/drive/special/approot/children")
    @Headers("Content-Type: application/json")
    suspend fun createFolder(@Body content: String): DriveItem

    @GET("v1.0/me/drive/special/approot:/{path}:/content")
    suspend fun downloadFileByPath(
        @Path("path") path: String
    ): String

    @GET("v1.0/me/drive/items/{itemId}/content")
    suspend fun downloadFileById(
        @Path("itemId") id: String
    ): String

    @PUT("v1.0/me/drive/special/approot:/{path}:/content")
    @Headers("Content-Type: text/plain")
    suspend fun createFile(
        @Path("path") path: String,
        @Body content: String
    ): DriveItem

    @PUT("v1.0/me/drive/items/{itemId}/content")
    @Headers("Content-Type: text/plain")
    suspend fun updateFile(
        @Path("itemId") id: String,
        @Body content: String
    )

    @DELETE("v1.0/me/drive/special/approot:/{path}")
    suspend fun delete(
        @Path("path", encoded = true) path: String
    )

    @DELETE("v1.0/me/drive/items/{itemId}")
    suspend fun deleteById(
        @Path("itemId") id: String
    )

    @GET("v1.0/me/drive/special/approot")
    suspend fun getAppRoot(): DriveItemId

    /**
     * Docs: https://learn.microsoft.com/en-us/graph/api/driveitem-delta
     */
    @GET("v1.0/me/drive/special/approot/delta")
    suspend fun getDelta(): DeltaResponse

    /**
     * Follows an `@odata.nextLink` or `@odata.deltaLink` from a previous [getDelta] call.
     */
    @GET("")
    suspend fun getDeltaByUrl(@Url url: String): DeltaResponse
}

@Serializable
internal data class OneDriveUserInfo(
    @SerialName("givenname")
    val givenName: String? = null,

    @SerialName("familyname")
    val familyName: String? = null,

    @SerialName("email")
    val email: String? = null
)

@Serializable
internal data class FileResponse(

    @SerialName("value")
    val files: List<DriveItem>
)

@Serializable
internal data class DriveItem(

    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("parentReference")
    val parent: ParentReference,

    @SerialName("size")
    val size: Long? = 0,

    @SerialName("lastModifiedDateTime")
    val lastModified: String,

    @SerialName("eTag")
    val eTag: String? = null,

    @SerialName("folder")
    val folder: Folder? = null
)

@Serializable
internal data class ParentReference(

    @SerialName("path")
    val path: String
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

@Serializable
internal data class DriveItemId(

    @SerialName("id")
    val id: String
)

@Serializable
internal data class DeltaResponse(

    @SerialName("value")
    val items: List<DeltaDriveItem>,

    /**
     * Present if there are more pages.
     */
    @SerialName("@odata.nextLink")
    val nextLink: String? = null,

    /**
     * Present on the last page, instead of [nextLink].
     */
    @SerialName("@odata.deltaLink")
    val deltaLink: String? = null
)

/**
 * Like [DriveItem], but delta responses omit some fields: `parentReference.path` is never
 * returned and deleted items lack most fields.
 */
@Serializable
internal data class DeltaDriveItem(

    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("parentReference")
    val parent: DeltaParentReference? = null,

    @SerialName("eTag")
    val eTag: String? = null,

    @SerialName("lastModifiedDateTime")
    val lastModified: String? = null,

    @SerialName("folder")
    val folder: Folder? = null,

    @SerialName("deleted")
    val deleted: Deleted? = null
)

@Serializable
internal data class DeltaParentReference(

    @SerialName("id")
    val id: String? = null
)

@Serializable
internal class Deleted