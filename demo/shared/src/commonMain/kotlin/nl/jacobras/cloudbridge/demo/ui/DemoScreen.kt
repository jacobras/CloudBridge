package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudService

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DemoScreen(
    viewModel: DemoViewModel,
    onAuthenticate: (CloudService) -> Unit,
    onFinishAuthOnWeb: (CloudService) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navigator = rememberListDetailPaneScaffoldNavigator()
    val connectedServices by viewModel.connectedServices.collectAsState()
    val selectedService by viewModel.selectedService.collectAsState()
    val scope = rememberCoroutineScope()
    var connectingWebDav by remember { mutableStateOf(false) }

    ListDetailPaneScaffold(
        modifier = modifier,
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            ServicesList(
                connectedServices = connectedServices,
                selectedService = selectedService,
                onServiceClick = {
                    viewModel.select(it)
                    scope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                    }
                },
                onAddClick = { availableService ->
                    if (availableService == AvailableService.WebDav) {
                        // WebDAV will show a dialog to enter credentials
                        connectingWebDav = true
                    } else {
                        onAuthenticate(availableService.toService())
                    }
                },
                onFinishAuthClick = { onFinishAuthOnWeb(it.toService()) }
            )
        },
        detailPane = {
            selectedService?.let { service ->
                DetailPane(
                    service = service,
                    userInfo = connectedServices[service],
                    onDisconnectClick = {
                        viewModel.disconnect(service)
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.List)
                        }
                    },
                    onBackClick = {
                        viewModel.deselect()
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.List)
                        }
                    }
                )
            } ?: Text(
                modifier = Modifier.padding(8.dp),
                text = "Select a service from the list"
            )
        },
        paneExpansionDragHandle = { _ -> VerticalDivider() }
    )

    if (connectingWebDav) {
        WebDavConnectDialog(
            onConfirm = { serverUrl, username, password ->
                viewModel.connectWebDav(serverUrl, username, password)
                connectingWebDav = false
            },
            onDismiss = { connectingWebDav = false }
        )
    }
}