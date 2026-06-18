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
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.model.FolderPath

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

    internal val selectedService: StateFlow<ServiceWithInfo?>
        field = MutableStateFlow<ServiceWithInfo?>(null)
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