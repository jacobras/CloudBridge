package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import nl.jacobras.cloudbridge.delta.DeltaCloudService
import nl.jacobras.cloudbridge.demo.DummyCloudService
import nl.jacobras.cloudbridge.model.CloudFile
import nl.jacobras.cloudbridge.model.UserInfo
import nl.jacobras.humanreadable.HumanReadable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailPane(
    service: CloudService,
    userInfo: UserInfo?,
    onDisconnectClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val viewModel = remember(service) { ServiceViewModel(service) }
    val path by viewModel.path.collectAsState()
    val files by viewModel.files.collectAsState()
    val error by viewModel.error.collectAsState()

    val selectedItem by viewModel.selectedItem.collectAsState()
    val content by viewModel.content.collectAsState()

    var pendingCreate by remember { mutableStateOf<CreateType?>(null) }

    val upload by viewModel.uploadProgress.collectAsState()
    var draggingFiles by remember { mutableStateOf(false) }

    val deltaCursor by viewModel.deltaCursor.collectAsState()
    val deltaResult by viewModel.deltaResult.collectAsState()
    var showDelta by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = buildString {
                        append(service.displayName)

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
                        Icon(
                            imageVector = if (path.isRoot) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (path.isRoot) {
                                "Close"
                            } else {
                                "Back"
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    // The dummy service is always signed in.
                    if (service !is DummyCloudService) {
                        Button(onClick = onDisconnectClick) {
                            Text("Sign out")
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { pendingCreate = CreateType.Folder }) {
                        Text("Create folder")
                    }
                    Button(onClick = { pendingCreate = CreateType.File }) {
                        Text("Create file")
                    }
                    if (isFilePickerSupported) {
                        Button(
                            onClick = { pickFiles(viewModel::uploadFiles) },
                            enabled = upload == null
                        ) {
                            Text("Upload file")
                        }
                    }
                    if (service is DeltaCloudService) {
                        Button(onClick = { showDelta = true }) {
                            Text("Delta")
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fileDropTarget(
                    onHoverChange = { hovering -> draggingFiles = hovering },
                    onDrop = viewModel::uploadFiles
                )
                .then(
                    if (draggingFiles) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                    } else {
                        Modifier
                    }
                )
        ) {
            val currentUpload = upload
            if (currentUpload != null) {
                UploadProgressRow(currentUpload)
                Spacer(Modifier.height(16.dp))
            } else if (isFileDropSupported) {
                Text(
                    text = "Drop files here to upload them to this folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            if (error.isNotBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
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
        CreateType.Folder -> EditDialog(
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

        CreateType.File -> EditDialog(
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

    if (showDelta) {
        DeltaDialog(
            cursor = deltaCursor,
            result = deltaResult,
            error = error,
            onFetch = viewModel::fetchDelta,
            onAdvance = viewModel::advanceDeltaCursor,
            onReset = viewModel::resetDeltaCursor,
            onDismiss = { showDelta = false }
        )
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

@Composable
private fun UploadProgressRow(progress: UploadProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = buildString {
                append("Uploading ${progress.fileName} (${HumanReadable.fileSize(progress.bytesRead)}")
                if (progress.totalBytes != null) {
                    append(" of ${HumanReadable.fileSize(progress.totalBytes)}")
                }
                append(")")
            },
            style = MaterialTheme.typography.bodyMedium
        )
        val totalBytes = progress.totalBytes
        if (totalBytes != null && totalBytes > 0) {
            LinearProgressIndicator(
                progress = { (progress.bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

private enum class CreateType {
    Folder,
    File
}