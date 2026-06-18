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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.jacobras.cloudbridge.CloudService

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DemoScreen(
    viewModel: DemoViewModel,
    onAuthenticate: (CloudService) -> Unit,
    onFinishAuthOnWeb: (CloudService) -> Unit = {}
) {
    val navigator = rememberListDetailPaneScaffoldNavigator()
    val userInfos by viewModel.userInfos.collectAsState()
    val selectedService by viewModel.selectedService.collectAsState()
    val scope = rememberCoroutineScope()

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            ServicesList(
                services = viewModel.services.associateWith { service -> userInfos[service] },
                selectedService = selectedService,
                onClick = {
                    viewModel.select(it)
                    scope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                    }
                }
            )
        },
        detailPane = {
            selectedService?.let { service ->
                DetailPane(
                    service = service,
                    userInfo = userInfos[service],
                    onAuthenticateClick = { onAuthenticate(service) },
                    onDeauthenticateClick = { viewModel.deauthenticate(service) },
                    onFinishAuthOnWeb = { onFinishAuthOnWeb(service) },
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
}