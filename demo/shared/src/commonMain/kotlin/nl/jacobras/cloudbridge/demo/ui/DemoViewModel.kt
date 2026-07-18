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
import nl.jacobras.cloudbridge.demo.DummyCloudService
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService

class DemoViewModel : ViewModel() {
    val dropbox = CloudBridge.dropbox()
    val googleDrive = CloudBridge.googleDrive()
    val oneDrive = CloudBridge.oneDrive()
    val dummy = DummyCloudService(null)
    val services = MutableStateFlow(
        mapOf<CloudService, UserInfo?>(
            dropbox to null,
            googleDrive to null,
            oneDrive to null,
            dummy to null
        )
    )

    internal val selectedService: StateFlow<CloudService?>
        field = MutableStateFlow<CloudService?>(null)

    private var loadServiceDetailsJob: Job? = null

    init {
        updateTokens()
    }

    fun updateTokens() = viewModelScope.launch {
        dropbox.setToken(DemoSettings.dropboxToken)
        googleDrive.setToken(DemoSettings.googleDriveToken)
        oneDrive.setToken(DemoSettings.oneDriveToken)
        services.update {
            val map = it.toMutableMap()
            map[dropbox] = try {
                dropbox.getUserInfo()
            } catch (_: Throwable) {
                null
            }
            map[googleDrive] = try {
                googleDrive.getUserInfo()
            } catch (_: Throwable) {
                null
            }
            map[oneDrive] = try {
                oneDrive.getUserInfo()
            } catch (_: Throwable) {
                null
            }
            map[dummy] = try {
                dummy.getUserInfo()
            } catch (_: Throwable) {
                null
            }
            map
        }
        selectedService.update { null }
    }

    internal fun select(service: CloudService) {
        loadServiceDetailsJob?.cancel()
        loadServiceDetailsJob = null
        selectedService.update { service }

        if (service.isAuthenticated()) {
            loadServiceDetailsJob = viewModelScope.launch {
                try {
                    val userInfo = service.getUserInfo()
                    services.update {
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
            }
            is GoogleDriveService -> {
                DemoSettings.googleDriveToken = null
                updateTokens()
            }
            is OneDriveService -> {
                DemoSettings.oneDriveToken = null
                updateTokens()
            }
            is DummyCloudService -> {
                service.setToken(null)
                updateTokens()
            }
        }
        services.update {
            val map = it.toMutableMap()
            map[service] = null
            map
        }
    }
}