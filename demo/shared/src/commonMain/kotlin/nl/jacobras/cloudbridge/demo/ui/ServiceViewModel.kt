package nl.jacobras.cloudbridge.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.asFilePath
import nl.jacobras.cloudbridge.model.asFolderPath

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
    val content: StateFlow<String>
        field = MutableStateFlow("")

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
        selectedItem.update { item }
        viewModelScope.launch {
            try {
                val items = service.downloadFile(item.id)
                content.update { items }
                error.update { "" }
            } catch (e: CloudServiceException) {
                error.update { e.toString() }
            }
        }
    }

    fun deselectItem() {
        selectedItem.update { null }
        content.update { "" }
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

    private fun childPath(name: String): String {
        return if (path.value.isRoot) "/$name" else "${path.value}/$name"
    }
}