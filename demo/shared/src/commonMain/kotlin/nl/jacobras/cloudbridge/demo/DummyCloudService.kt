package nl.jacobras.cloudbridge.demo

import kotlinx.io.Source
import kotlinx.io.readString
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.OAuthCloudService
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.delta.DeletedDeltaChange
import nl.jacobras.cloudbridge.delta.DeltaChange
import nl.jacobras.cloudbridge.delta.DeltaCloudService
import nl.jacobras.cloudbridge.delta.DeltaCursor
import nl.jacobras.cloudbridge.delta.DeltaResult
import nl.jacobras.cloudbridge.delta.UpdatedDeltaChange
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
import kotlin.time.Instant

class DummyCloudService(
    startToken: CloudAccessToken?
) : OAuthCloudService(startToken), DeltaCloudService {

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
            modified = Clock.System.now(),
            revision = "rev-0"
        )
    )
    private var cloudItemContents = mutableMapOf(
        CloudItemId("456") to "Hello world!"
    )

    /**
     * All file/folder changes in chronological order. A cursor is an index into this list.
     */
    private val changeLog = mutableListOf<DeltaChange>()
    private val revisions = mutableMapOf<CloudItemId, Int>()

    /**
     * [CloudFolder] has no modified date, so track it here. Folders that existed
     * from the start fall back to [startTime].
     */
    private val folderModified = mutableMapOf<CloudItemId, Instant>()
    private val startTime = Clock.System.now()

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

        return cloudItems.filter { it.path.startsWith(path) }
    }

    override suspend fun getFileInfo(path: FilePath): CloudFile {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        return cloudItems.firstOrNull { it is CloudFile && it.path.toString() == path.toString() }
                as? CloudFile ?: throw CloudServiceException.NotFoundException(path.toString())
    }

    override suspend fun getFolderInfo(path: FolderPath): CloudFolder {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        require(!path.isRoot) { "Can't get info for the root folder" }
        return cloudItems.firstOrNull { it is CloudFolder && it.path.toString() == path.toString() }
                as? CloudFolder ?: throw CloudServiceException.NotFoundException(path.toString())
    }

    override suspend fun createFolder(path: FolderPath): CloudFolder {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val folder = CloudFolder(
            id = CloudItemId(path.hashCode().toString()),
            path = path,
            name = path.name
        )
        cloudItems += folder
        folderModified[folder.id] = Clock.System.now()
        recordUpdate(folder)
        return folder
    }

    override suspend fun createFile(
        path: FilePath,
        content: String
    ): CloudFile {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val id = CloudItemId(path.hashCode().toString())
        val file = CloudFile(
            id = id,
            path = path,
            name = path.name,
            sizeInBytes = content.length * 8L,
            modified = Clock.System.now(),
            revision = nextRevision(id)
        )
        cloudItems += file
        cloudItemContents[id] = content
        recordUpdate(file)
        return file
    }

    override suspend fun updateFile(
        id: CloudItemId,
        content: String
    ): CloudFile {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val existing = cloudItems.firstOrNull { it.id == id }
                as? CloudFile ?: throw CloudServiceException.NotFoundException(id.value)
        val updated = existing.copy(
            sizeInBytes = content.length * 8L,
            modified = Clock.System.now(),
            revision = nextRevision(id)
        )
        cloudItems -= existing
        cloudItems += updated
        cloudItemContents[id] = content
        recordUpdate(updated)
        return updated
    }

    override suspend fun createFile(
        path: FilePath,
        source: Source,
        sizeInBytes: Long?,
        mimeType: String
    ): CloudFile = createFile(path, source.readString())

    override suspend fun updateFile(
        id: CloudItemId,
        source: Source,
        sizeInBytes: Long?,
        mimeType: String
    ): CloudFile = updateFile(id, source.readString())

    override suspend fun downloadFile(id: CloudItemId): String {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        return cloudItemContents[id] ?: throw CloudServiceException.NotFoundException(id.value)
    }

    override suspend fun moveFile(id: CloudItemId, newPath: FilePath): CloudFile {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val existing = cloudItems.firstOrNull { it.id == id }
                as? CloudFile ?: throw CloudServiceException.NotFoundException(id.value)
        val renamed = existing.copy(
            name = newPath.name,
            path = newPath,
            revision = nextRevision(id)
        )
        if (cloudItems.any { it.path.toString() == newPath.toString() }) {
            throw CloudServiceException.ConflictException(newPath.toString())
        }
        cloudItems -= existing
        cloudItems += renamed
        recordUpdate(renamed)
        return renamed
    }

    override suspend fun moveFolder(id: CloudItemId, newPath: FolderPath): CloudFolder {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val existing = cloudItems.firstOrNull { it.id == id }
                as? CloudFolder ?: throw CloudServiceException.NotFoundException(id.value)
        val renamed = existing.copy(
            name = newPath.name,
            path = newPath
        )
        if (cloudItems.any { it.path.toString() == newPath.toString() }) {
            throw CloudServiceException.ConflictException(newPath.toString())
        }
        cloudItems -= existing
        cloudItems += renamed
        folderModified[id] = Clock.System.now()
        recordUpdate(renamed)
        return renamed
    }

    override suspend fun delete(id: CloudItemId) {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val existing = cloudItems.firstOrNull { it.id == id }
                as? CloudFile ?: throw CloudServiceException.NotFoundException(id.value)
        cloudItems -= existing
        cloudItemContents.remove(id)
        changeLog += DeletedDeltaChange(itemId = id, path = existing.path)
    }

    override suspend fun getDelta(previousCursor: DeltaCursor): DeltaResult {
        if (!isAuthenticated()) {
            throw CloudServiceException.NotAuthenticatedException()
        }

        val changes = if (previousCursor == DeltaCursor.INITIAL) {
            cloudItems.map { it.toUpdatedChange() }
        } else {
            val index = previousCursor.value.toIntOrNull()
                ?.takeIf { it in 0..changeLog.size }
                ?: throw CloudServiceException.NotFoundException(previousCursor.value)

            // Only report the latest change per item, like real services do.
            changeLog.drop(index).associateBy { it.itemId }.values.toList()
        }

        return DeltaResult(
            newCursor = DeltaCursor(changeLog.size.toString()),
            changes = changes
        )
    }

    private fun recordUpdate(item: CloudItem) {
        revisions[item.id] = (revisions[item.id] ?: 0) + 1
        changeLog += item.toUpdatedChange()
    }

    /**
     * The revision [id] will have after the next [recordUpdate].
     */
    private fun nextRevision(id: CloudItemId): String = "rev-${(revisions[id] ?: 0) + 1}"

    private fun CloudItem.toUpdatedChange(): UpdatedDeltaChange {
        return UpdatedDeltaChange(
            itemId = id,
            path = path,
            name = name,
            revision = "rev-${revisions[id] ?: 0}",
            modified = when (this) {
                is CloudFile -> modified
                is CloudFolder -> folderModified[id] ?: startTime
            }
        )
    }
}
