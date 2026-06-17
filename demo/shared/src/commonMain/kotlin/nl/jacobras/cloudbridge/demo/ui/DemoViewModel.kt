package nl.jacobras.cloudbridge.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloudbridge.demo.shared.generated.resources.Res
import cloudbridge.demo.shared.generated.resources.ic_dropbox
import cloudbridge.demo.shared.generated.resources.ic_google_drive
import cloudbridge.demo.shared.generated.resources.ic_one_drive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.model.CloudItem
import nl.jacobras.cloudbridge.model.FolderPath
import nl.jacobras.cloudbridge.model.UserInfo

class DemoViewModel : ViewModel() {
    val dropbox = CloudBridge.dropbox()
    val googleDrive = CloudBridge.googleDrive()
    val oneDrive = CloudBridge.oneDrive()
    internal val services = listOf(
        ServiceWithInfo(
            service = dropbox,
            logo = Res.drawable.ic_dropbox,
            name = "Dropbox"
        ),
        ServiceWithInfo(
            service = googleDrive,
            logo = Res.drawable.ic_google_drive,
            name = "Google Drive"
        ),
        ServiceWithInfo(
            service = oneDrive,
            logo = Res.drawable.ic_one_drive,
            name = "OneDrive"
        )
    )

    val files = MutableStateFlow<Map<CloudService, List<CloudItem>>>(emptyMap())
    val userInfo = MutableStateFlow<Map<CloudService, UserInfo?>>(emptyMap())
    val serviceErrors = MutableStateFlow<Map<CloudService, String>>(emptyMap())

    internal val selectedService: StateFlow<ServiceWithInfo?>
        field = MutableStateFlow<ServiceWithInfo?>(null)
    val path = MutableStateFlow(FolderPath("/"))

    private var loadServiceDetailsJob : Job?=null

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

        try {
            val info = service.getUserInfo()
            userInfo.update {
                val map = it.toMutableMap()
                map[service] = info
                map
            }
        } catch (_: Exception) {
            userInfo.update {
                val map = it.toMutableMap()
                map[service] = null
                map
            }
        }

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

    internal fun select(info: ServiceWithInfo) {
        loadServiceDetailsJob?.cancel()
        loadServiceDetailsJob = null
        selectedService.update { info }

        val service = info.service.takeIf { it.isAuthenticated() } ?: return
        loadServiceDetailsJob = viewModelScope.launch {
            try {
                val userInfo = service.getUserInfo()
                val userName = userInfo.name
                if (userName != null) {
                    selectedService.update { info.copy(userName = userName) }
                }
            } catch (_: CloudServiceException) {
                // NOOP
            }
        }
    }

    internal fun deselect() {
        selectedService.update { null }
    }
}