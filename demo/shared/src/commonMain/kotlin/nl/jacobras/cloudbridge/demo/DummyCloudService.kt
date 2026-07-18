package nl.jacobras.cloudbridge.demo

import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.OAuthCloudService
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FilePath
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import kotlin.time.Clock

class DummyCloudService(startToken: CloudAccessToken?) : OAuthCloudService(startToken) {

    override val baseUrl: String = ""

    private var cloudItems = mutableListOf(
        CloudFolder(id = CloudItemId("123"), path = "/Cats".asFolderPath(), name = "Cats"),
        CloudFolder(id = CloudItemId("234"), path = "/Goats".asFolderPath(), name = "Goats"),
        CloudFolder(id = CloudItemId("345"), path = "/Birds".asFolderPath(), name = "Birds"),
        CloudFile(
            id = CloudItemId("456"),
            path = "/Welcome.txt".asFilePath(),
            name = "Welcome.txt",
            sizeInBytes = 123L,
            modified = Clock.System.now()
        )
    )
    private var cloudItemContents = mutableMapOf(
        CloudItemId("456") to "Hello world!"
    )

    override suspend fun getUserInfo(): UserInfo {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        return UserInfo(name = "Dummy", emailAddress = "dummy@example.com")
    }

    override suspend fun listFiles(path: FolderPath): List<CloudItem> {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        return cloudItems.filter { it.path.startsWith(path) && it.path != path }
    }

    override suspend fun createFolder(path: FolderPath) {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        cloudItems += CloudFolder(
            id = CloudItemId(path.hashCode().toString()),
            path = path,
            name = path.name
        )
    }

    override suspend fun createFile(
        path: FilePath,
        content: String
    ) {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val id = CloudItemId(path.hashCode().toString())
        cloudItems += CloudFile(
            id = id,
            path = path,
            name = path.name,
            sizeInBytes = content.length * 8L,
            modified = Clock.System.now()
        )
        cloudItemContents[id] = content
    }

    override suspend fun updateFile(
        id: CloudItemId,
        content: String
    ) {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val existing = cloudItems.firstOrNull { it.id == id }
                as? CloudFile ?: throw CloudServiceException.NotFoundException(id.value)
        cloudItems -= existing
        cloudItems += existing.copy(
            sizeInBytes = content.length * 8L,
            modified = Clock.System.now()
        )
        cloudItemContents[id] = content
    }

    override suspend fun downloadFile(id: CloudItemId): String {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        return cloudItemContents[id] ?: throw CloudServiceException.NotFoundException(id.value)
    }

    override suspend fun delete(id: CloudItemId) {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val existing = cloudItems.firstOrNull { it.id == id }
                as? CloudFile ?: throw CloudServiceException.NotFoundException(id.value)
        cloudItems -= existing
        cloudItemContents.remove(id)
    }
}