package nl.jacobras.cloudbridge.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.delta.DeltaCloudService
import nl.jacobras.cloudbridge.delta.DeltaCursor
import nl.jacobras.cloudbridge.delta.DeltaResult
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.CloudFolder
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath
import org.jetbrains.compose.resources.decodeToImageBitmap

internal class ServiceViewModel(
    private val service: CloudService
) : ViewModel() {
    val path: StateFlow<FolderPath>
        field = MutableStateFlow("".asFolderPath())
    val files: StateFlow<List<CloudItem>>
        field = MutableStateFlow(emptyList())
    val error: StateFlow<String>
        field = MutableStateFlow("")

    val selectedItem: StateFlow<CloudItem?>
        field = MutableStateFlow(null)
    val content: StateFlow<FileContent>
        field = MutableStateFlow<FileContent>(FileContent.None)
    val isDownloading: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val uploadProgress: StateFlow<UploadProgress?>
        field = MutableStateFlow(null)

    val deltaCursor: StateFlow<DeltaCursor>
        field = MutableStateFlow(DeltaCursor.INITIAL)
    val deltaResult: StateFlow<DeltaResult?>
        field = MutableStateFlow(null)

    init {
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        try {
            val items = service.listFiles(path.value)
            files.update { items }
            error.update { "" }
        } catch (e: CloudServiceException) {
            error.update { e.toString() }
        }
    }

    fun selectItem(item: CloudItem) {
        when (item) {
            is CloudFile -> selectFile(item)
            is CloudFolder -> selectFolder(item)
        }
    }

    private fun selectFile(file: CloudFile) {
        selectedItem.update { file }
        val type = FileType.of(file.name)
        if (type == FileType.Other) {
            // Only downloaded when requested, see [downloadSelectedFile].
            content.update { FileContent.None }
            return
        }

        content.update { FileContent.Loading }
        viewModelScope.launch {
            try {
                val loaded = when (type) {
                    FileType.Text -> FileContent.Text(service.downloadFile(file.id))
                    else -> {
                        val bytes = downloadBytes(file)
                        val bitmap = runCatching { bytes.decodeToImageBitmap() }.getOrNull()
                        if (bitmap != null) FileContent.Image(bitmap, bytes) else FileContent.None
                    }
                }
                if (selectedItem.value == file) {
                    content.update { loaded }
                }
                error.update { "" }
            } catch (e: CloudServiceException) {
                content.update { FileContent.None }
                error.update { e.toString() }
            }
        }
    }

    /**
     * Downloads the selected file and lets the user save it locally.
     */
    fun downloadSelectedFile() = viewModelScope.launch {
        val file = selectedItem.value as? CloudFile ?: return@launch
        if (isDownloading.value) return@launch

        isDownloading.update { true }
        try {
            val bytes = (content.value as? FileContent.Image)?.bytes ?: downloadBytes(file)
            saveFile(file.name, bytes)
            error.update { "" }
        } catch (e: CloudServiceException) {
            error.update { e.toString() }
        } catch (e: IOException) {
            error.update { e.toString() }
        } finally {
            isDownloading.update { false }
        }
    }

    private suspend fun downloadBytes(file: CloudFile): ByteArray {
        val buffer = Buffer()
        service.downloadFile(file.id, buffer)
        return buffer.readByteArray()
    }

    private fun selectFolder(folder: CloudFolder) {
        selectedItem.update { null }
        path.update { folder.path }
        refresh()
    }

    internal fun navigatePathUp() {
        path.update { it.parent }
        refresh()
    }

    fun deselectItem() {
        selectedItem.update { null }
        content.update { FileContent.None }
    }

    fun createFolder(name: String) = viewModelScope.launch {
        try {
            service.createFolder(childPath(name).asFolderPath())
            refresh()
        } catch (e: CloudServiceException) {
            error.update { e.toString() }
        }
    }

    fun createFile(name: String, content: String) = viewModelScope.launch {
        try {
            service.createFile(childPath(name).asFilePath(), content)
            refresh()
        } catch (e: CloudServiceException) {
            error.update { e.toString() }
        }
    }

    /**
     * Uploads [files] one by one into the current folder.
     */
    fun uploadFiles(files: List<LocalFile>) = viewModelScope.launch {
        if (uploadProgress.value != null) return@launch

        try {
            for (file in files) {
                uploadProgress.update {
                    UploadProgress(
                        file.name,
                        bytesRead = 0,
                        totalBytes = file.sizeInBytes
                    )
                }
                val source = ProgressSource(file.open()) { bytesRead ->
                    uploadProgress.update { it?.copy(bytesRead = bytesRead) }
                }.buffered()
                source.use {
                    service.createFile(
                        path = childPath(file.name).asFilePath(),
                        source = it,
                        sizeInBytes = file.sizeInBytes,
                        mimeType = file.mimeType ?: "application/octet-stream"
                    )
                }
            }
            error.update { "" }
        } catch (e: CloudServiceException) {
            error.update { e.toString() }
        } catch (e: IllegalArgumentException) {
            error.update { e.toString() }
        } catch (e: IOException) {
            error.update { e.toString() }
        } finally {
            uploadProgress.update { null }
            refresh()
        }
    }

    fun updateFile(id: CloudItemId, content: String) = viewModelScope.launch {
        try {
            service.updateFile(id, content)
            deselectItem()
            refresh()
        } catch (e: CloudServiceException) {
            error.update { e.toString() }
        }
    }

    fun delete(item: CloudItem) = viewModelScope.launch {
        try {
            service.delete(item.id)
            deselectItem()
            refresh()
        } catch (e: CloudServiceException) {
            error.update { e.toString() }
        }
    }

    /**
     * Fetches the changes since [deltaCursor] without moving the cursor forward.
     */
    fun fetchDelta() = viewModelScope.launch {
        val deltaService = service as? DeltaCloudService ?: return@launch
        try {
            val result = deltaService.getDelta(deltaCursor.value)
            deltaResult.update { result }
            error.update { "" }
        } catch (e: CloudServiceException) {
            error.update { e.toString() }
        }
    }

    fun advanceDeltaCursor() {
        val result = deltaResult.value ?: return
        deltaCursor.update { result.newCursor }
        deltaResult.update { null }
    }

    fun resetDeltaCursor() {
        deltaCursor.update { DeltaCursor.INITIAL }
        deltaResult.update { null }
    }

    private fun childPath(name: String): String {
        return if (path.value.isRoot) "/$name" else "${path.value}/$name"
    }
}