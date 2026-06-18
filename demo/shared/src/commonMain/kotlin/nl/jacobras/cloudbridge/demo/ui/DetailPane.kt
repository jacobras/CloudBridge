package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.isWeb
import nl.jacobras.cloudbridge.model.UserInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailPane(
    service: CloudService,
    userInfo: UserInfo?,
    onAuthenticateClick: () -> Unit,
    onDeauthenticateClick: () -> Unit,
    onFinishAuthOnWeb: () -> Unit,
    onBackClick: () -> Unit
) {
    val viewModel = remember(service) { ServiceViewModel(service) }
    val files by viewModel.files.collectAsState()
    val error by viewModel.error.collectAsState()

    val selectedItem by viewModel.selectedItem.collectAsState()
    val content by viewModel.content.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = buildString {
                        append(service.name)

                        if (userInfo?.emailAddress != null) {
                            append(" (${userInfo.emailAddress})")
                        }
                    })
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    if (!service.isAuthenticated()) {
                        Button(onClick = onAuthenticateClick) {
                            Text("Sign in")
                        }
                    } else {
                        Button(onClick = onDeauthenticateClick) {
                            Text("Sign out")
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            )
        },
        containerColor = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(it)) {
            if (error.isNotBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))
            }

            if (isWeb && !service.isAuthenticated()) {
                Button(onClick = onFinishAuthOnWeb) {
                    Text("Finish auth")
                }
                Spacer(Modifier.height(16.dp))
            }

            if (content.isNotEmpty() && selectedItem != null) {
                FileDetailDialog(
                    service = service,
                    content = content,
                    item = selectedItem!!,
                    onDismiss = { viewModel.deselectItem() }
                )
            }

            FilesList(
                modifier = Modifier.fillMaxSize(),
                files = files,
                onItemClick = viewModel::selectItem
            )
        }
    }
}