package nl.jacobras.cloudbridge.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.CloudBridgeException
import nl.jacobras.cloudbridge.CloudService
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
        field = MutableStateFlow(mapOf<CloudService, UserInfo?>(dummy to null))

    internal val selectedService: StateFlow<CloudService?>
        field = MutableStateFlow<CloudService?>(null)

    /**
     * Connected instances in [connectedServices].
     */
    private val services = mutableMapOf<AvailableService, CloudService>()

    init {
        setUpLogging()
        AvailableService.entries.forEach { type ->
            getConnectedService(type)?.let { setService(type, it) }
        }
        viewModelScope.launch { loadUserInfo(dummy) }
    }

    /**
     * Sets [service] as the connected instance of [type], or removes it if `null`. The previous
     * instance gets its token cleared, so a late token refresh can't restore deleted credentials
     * or overwrite a newer login.
     */
    private fun setService(type: AvailableService, service: CloudService?) = viewModelScope.launch {
        val previous = if (service != null) {
            services.put(type, service)
        } else {
            services.remove(type)
        }
        (previous as? OAuthCloudService)?.setToken(null)

        val current = AvailableService.entries.mapNotNull(services::get) + dummy
        connectedServices.value = current.associateWith { connectedServices.value[it] }
        if (selectedService.value == previous) {
            selectedService.value = null
        }
        service?.let { loadUserInfo(it) }
    }

    private suspend fun loadUserInfo(service: CloudService) {
        val userInfo = try {
            service.getUserInfo()
        } catch (_: CloudBridgeException) {
            return
        }
        connectedServices.update { if (service in it) it + (service to userInfo) else it }
    }

    fun connectDropbox(token: CloudAccessToken) {
        DemoSettings.dropboxToken = token
        setService(AvailableService.Dropbox, dropbox(token))
    }

    fun connectGoogleDrive(token: CloudAccessToken) {
        DemoSettings.googleDriveToken = token
        setService(AvailableService.GoogleDrive, googleDrive(token))
    }

    fun connectOneDrive(token: CloudAccessToken) {
        DemoSettings.oneDriveToken = token
        setService(AvailableService.OneDrive, oneDrive(token))
    }

    fun connectWebDav(serverUrl: String, username: String, password: String) {
        DemoSettings.webDavServerUrl = serverUrl
        DemoSettings.webDavUsername = username
        DemoSettings.webDavPassword = password
        val credentials = WebDavCredentials(serverUrl, username, password)
        setService(AvailableService.WebDav, CloudBridge.webDav(credentials))
    }

    fun disconnect(service: CloudService) {
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
        services.entries.find { it.value == service }?.key?.let { setService(it, null) }
    }

    internal fun select(service: CloudService) {
        selectedService.value = service
        viewModelScope.launch { loadUserInfo(service) }
    }

    internal fun deselect() {
        selectedService.value = null
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
            AvailableService.Dropbox -> DemoSettings.dropboxToken?.let(::dropbox)
            AvailableService.GoogleDrive -> DemoSettings.googleDriveToken?.let(::googleDrive)
            AvailableService.OneDrive -> DemoSettings.oneDriveToken?.let(::oneDrive)
            AvailableService.WebDav -> getWebDavCredentials()?.let { CloudBridge.webDav(it) }
        }
    }

    private fun dropbox(token: CloudAccessToken) = CloudBridge.dropbox(
        clientId = dropboxClientId,
        token = token,
        onTokenRefreshed = { DemoSettings.dropboxToken = it }
    )

    private fun googleDrive(token: CloudAccessToken) = CloudBridge.googleDrive(
        clientId = googleDriveClientId,
        desktopClientSecret = googleDriveClientSecret,
        token = token,
        onTokenRefreshed = { DemoSettings.googleDriveToken = it }
    )

    private fun oneDrive(token: CloudAccessToken) = CloudBridge.oneDrive(
        clientId = oneDriveClientId,
        token = token,
        onTokenRefreshed = { DemoSettings.oneDriveToken = it }
    )

    private fun getWebDavCredentials(): WebDavCredentials? {
        return WebDavCredentials(
            serverUrl = DemoSettings.webDavServerUrl ?: return null,
            username = DemoSettings.webDavUsername ?: return null,
            password = DemoSettings.webDavPassword ?: return null
        )
    }
}