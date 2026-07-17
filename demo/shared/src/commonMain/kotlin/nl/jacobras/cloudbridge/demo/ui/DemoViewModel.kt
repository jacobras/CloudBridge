package nl.jacobras.cloudbridge.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService

class DemoViewModel : ViewModel() {
    val dropbox = CloudBridge.dropbox()
    val googleDrive = CloudBridge.googleDrive()
    val oneDrive = CloudBridge.oneDrive()
    val services = listOf(dropbox, googleDrive, oneDrive)

    internal val userInfos: StateFlow<Map<CloudService, UserInfo>>
        field = MutableStateFlow(emptyMap())

    internal val selectedService: StateFlow<CloudService?>
        field = MutableStateFlow<CloudService?>(null)
    val path = MutableStateFlow(FolderPath("/"))

    private var loadServiceDetailsJob: Job? = null

    init {
        updateTokens()
    }

    fun updateTokens() {
        dropbox.setToken(DemoSettings.dropboxToken)
        googleDrive.setToken(DemoSettings.googleDriveToken)
        oneDrive.setToken(DemoSettings.oneDriveToken)
    }

    internal fun select(service: CloudService) {
        loadServiceDetailsJob?.cancel()
        loadServiceDetailsJob = null
        selectedService.update { service }

        if (service.isAuthenticated()) {
            loadServiceDetailsJob = viewModelScope.launch {
                try {
                    val userInfo = service.getUserInfo()
                    userInfos.update {
                        val map = it.toMutableMap()
                        map[service] = userInfo
                        map
                    }
                } catch (_: CloudServiceException) {
                    // NOOP
                }
            }
        }
    }

    internal fun deselect() {
        selectedService.update { null }
    }

    fun deauthenticate(service: CloudService) {
        when (service) {
            is DropboxService -> {
                DemoSettings.dropboxToken = null
                updateTokens()
                userInfos.update { it - service }
            }
            is GoogleDriveService -> {
                DemoSettings.googleDriveToken = null
                updateTokens()
                userInfos.update { it - service }
            }
            is OneDriveService -> {
                DemoSettings.oneDriveToken = null
                updateTokens()
                userInfos.update { it - service }
            }
        }
    }
}