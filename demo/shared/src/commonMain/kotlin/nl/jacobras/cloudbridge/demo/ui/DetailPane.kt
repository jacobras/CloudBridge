package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.isWeb
import nl.jacobras.cloudbridge.model.CloudFile
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
    val path by viewModel.path.collectAsState()
    val files by viewModel.files.collectAsState()
    val error by viewModel.error.collectAsState()

    val selectedItem by viewModel.selectedItem.collectAsState()
    val content by viewModel.content.collectAsState()

    var pendingCreate by remember { mutableStateOf<CreateType?>(null) }

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
                    IconButton(onClick = {
                        if (path.isRoot) {
                            onBackClick()
                        } else {
                            viewModel.navigatePathUp()
                        }
                    }) {
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
        bottomBar = {
            if (service.isAuthenticated()) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { pendingCreate = CreateType.FOLDER }) {
                            Text("Create folder")
                        }
                        Button(onClick = { pendingCreate = CreateType.FILE }) {
                            Text("Create file")
                        }
                    }
                }
            }
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

            FilesList(
                modifier = Modifier.fillMaxSize(),
                files = files,
                onItemClick = viewModel::selectItem
            )
        }
    }

    when (pendingCreate) {
        CreateType.FOLDER -> EditDialog(
            title = "New folder",
            confirmLabel = "Create",
            nameEditable = true,
            showContent = false,
            onConfirm = { name, _ ->
                viewModel.createFolder(name)
                pendingCreate = null
            },
            onDismiss = { pendingCreate = null }
        )

        CreateType.FILE -> EditDialog(
            title = "New file",
            confirmLabel = "Create",
            nameEditable = true,
            showContent = true,
            onConfirm = { name, content ->
                viewModel.createFile(name, content)
                pendingCreate = null
            },
            onDismiss = { pendingCreate = null }
        )

        null -> Unit
    }

    val editingFile = selectedItem
    if (editingFile is CloudFile) {
        EditDialog(
            title = "Edit file",
            confirmLabel = "Save",
            nameEditable = false,
            showContent = true,
            initialName = editingFile.name,
            initialContent = content,
            onConfirm = { _, newContent -> viewModel.updateFile(editingFile.id, newContent) },
            onDismiss = { viewModel.deselectItem() },
            onDelete = { viewModel.delete(editingFile) }
        )
    }
}

private enum class CreateType {
    FOLDER,
    FILE
}