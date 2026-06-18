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
import nl.jacobras.cloudbridge.model.FolderPath
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
}