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
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.auth.CloudAccessToken
import nl.jacobras.cloudbridge.demo.DummyCloudService
import nl.jacobras.cloudbridge.demo.persistence.DemoSettings
import nl.jacobras.cloudbridge.demo.setUpLogging
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.cloudbridge.service.dropbox.DropboxService
import nl.jacobras.cloudbridge.service.googledrive.GoogleDriveService
import nl.jacobras.cloudbridge.service.oauth.OAuthCloudService
import nl.jacobras.cloudbridge.service.onedrive.OneDriveService
import nl.jacobras.cloudbridge.service.webdav.WebDavCredentials
import nl.jacobras.cloudbridge.service.webdav.WebDavService

class DemoViewModel(
    private val dropboxClientId: String,
    private val googleDriveClientId: String,
    private val googleDriveClientSecret: String? = null,
    private val oneDriveClientId: String
) : ViewModel() {

    private val dummy = DummyCloudService(CloudAccessToken(accessToken = "fake"))

    val connectedServices: StateFlow<Map<CloudService, UserInfo?>>
        field = MutableStateFlow(emptyMap<CloudService, UserInfo?>())

    internal val selectedService: StateFlow<CloudService?>
        field = MutableStateFlow<CloudService?>(null)

    private var loadServiceDetailsJob: Job? = null

    /**
     * Connected instances in [connectedServices].
     */
    private var services = emptyList<CloudService>()

    init {
        setUpLogging()
        refresh()
    }

    /**
     * Rebuilds [connectedServices] from the credentials in [DemoSettings].
     */
    fun refresh() = viewModelScope.launch {
        services.forEach { (it as? OAuthCloudService)?.setToken(null) }
        services = AvailableService.entries.mapNotNull(::getConnectedService)

        connectedServices.update {
            (services + dummy).associateWith { service ->
                try {
                    service.getUserInfo()
                } catch (_: Throwable) {
                    null
                }
            }
        }
        selectedService.update { null }
    }

    fun connectWebDav(serverUrl: String, username: String, password: String) {
        DemoSettings.webDavServerUrl = serverUrl
        DemoSettings.webDavUsername = username
        DemoSettings.webDavPassword = password
        refresh()
    }

    fun disconnect(service: CloudService) {
        loadServiceDetailsJob?.cancel()
        loadServiceDetailsJob = null
        (service as? OAuthCloudService)?.setToken(null)

        when (service) {
            is DropboxService -> DemoSettings.dropboxToken = null
            is GoogleDriveService -> DemoSettings.googleDriveToken = null
            is OneDriveService -> DemoSettings.oneDriveToken = null
            is WebDavService -> {
                DemoSettings.webDavServerUrl = null
                DemoSettings.webDavUsername = null
                DemoSettings.webDavPassword = null
            }
        }
        refresh()
    }

    internal fun select(service: CloudService) {
        loadServiceDetailsJob?.cancel()
        loadServiceDetailsJob = null
        selectedService.update { service }

        loadServiceDetailsJob = viewModelScope.launch {
            try {
                val userInfo = service.getUserInfo()
                connectedServices.update {
                    val map = it.toMutableMap()
                    map[service] = userInfo
                    map
                }
            } catch (_: CloudBridgeException) {
                // NOOP
            }
        }
    }

    internal fun deselect() {
        selectedService.update { null }
    }

    /**
     * Creates an instance of [service], to authenticate with. Not used for WebDAV.
     */
    internal fun createService(service: AvailableService): CloudService {
        return when (service) {
            AvailableService.Dropbox -> CloudBridge.dropbox(dropboxClientId)
            AvailableService.GoogleDrive -> CloudBridge.googleDrive(
                clientId = googleDriveClientId,
                desktopClientSecret = googleDriveClientSecret
            )
            AvailableService.OneDrive -> CloudBridge.oneDrive(oneDriveClientId)
            AvailableService.WebDav -> error("WebDAV requires credentials, use CloudBridge.webDav()")
        }
    }

    /**
     * Creates an instance of [service] with the stored credentials, or `null` if there are none.
     */
    private fun getConnectedService(service: AvailableService): CloudService? {
        return when (service) {
            AvailableService.Dropbox -> DemoSettings.dropboxToken?.let {
                CloudBridge.dropbox(
                    clientId = dropboxClientId,
                    token = it,
                    onTokenRefreshed = { token -> DemoSettings.dropboxToken = token }
                )
            }
            AvailableService.GoogleDrive -> DemoSettings.googleDriveToken?.let {
                CloudBridge.googleDrive(
                    clientId = googleDriveClientId,
                    desktopClientSecret = googleDriveClientSecret,
                    token = it,
                    onTokenRefreshed = { token -> DemoSettings.googleDriveToken = token }
                )
            }
            AvailableService.OneDrive -> DemoSettings.oneDriveToken?.let {
                CloudBridge.oneDrive(
                    clientId = oneDriveClientId,
                    token = it,
                    onTokenRefreshed = { token -> DemoSettings.oneDriveToken = token }
                )
            }
            AvailableService.WebDav -> getWebDavCredentials()?.let { CloudBridge.webDav(it) }
        }
    }

    private fun getWebDavCredentials(): WebDavCredentials? {
        val serverUrl = DemoSettings.webDavServerUrl ?: return null
        val username = DemoSettings.webDavUsername ?: return null
        val password = DemoSettings.webDavPassword ?: return null
        return WebDavCredentials(
            serverUrl = serverUrl,
            username = username,
            password = password
        )
    }
}