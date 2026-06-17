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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.model.CloudItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailPane(
    info: ServiceWithInfo,
    files: List<CloudItem>,
    error: String,
    onAuthenticateClick: () -> Unit,
    onDeauthenticateClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = buildString {
                        append(info.name)

                        if (info.emailAddress.isNotBlank()) {
                            append(" (${info.emailAddress})")
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
                    val service = info.service
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

            FilesList(
                modifier = Modifier.fillMaxSize(),
                serviceWithInfo = info,
                files = files,
                onBackClick = onBackClick,
                onItemClick = {
                    // TODO
                }
            )
        }
    }
}