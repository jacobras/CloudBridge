package nl.jacobras.cloudbridge.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.UserInfo

internal class MainViewModel : ViewModel() {
    val dropbox = CloudBridge.dropbox()
    val googleDrive = CloudBridge.googleDrive()
    val oneDrive = CloudBridge.oneDrive()

    val files = MutableStateFlow<Map<CloudService, List<CloudItem>>>(emptyMap())
    val userInfo = MutableStateFlow<Map<CloudService, UserInfo?>>(emptyMap())
    val serviceErrors = MutableStateFlow<Map<CloudService, String>>(emptyMap())

    val path = MutableStateFlow(FolderPath("/"))

    init {
        updateTokens()
        refresh(dropbox)
        refresh(googleDrive)
        refresh(oneDrive)
    }

    fun updateTokens() {
        dropbox.setToken(DemoSettings.dropboxToken)
        googleDrive.setToken(DemoSettings.googleDriveToken)
        oneDrive.setToken(DemoSettings.oneDriveToken)
    }

    fun refresh(service: CloudService) = viewModelScope.launch {
        updateTokens()
        val updated = try {
            val files = service.listFiles(path.value)
            serviceErrors.update {
                val map = it.toMutableMap()
                map[service] = ""
                map
            }
            files
        } catch (e: Exception) {
            serviceErrors.update {
                val map = it.toMutableMap()
                map[service] = e.toString()
                map
            }
            emptyList()
        }

        files.update {
            val map = it.toMutableMap()
            map[service] = updated
            map
        }
    }
}