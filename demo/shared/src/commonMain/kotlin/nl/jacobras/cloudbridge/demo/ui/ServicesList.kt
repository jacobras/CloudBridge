package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.CloudService
import nl.jacobras.cloudbridge.demo.isWeb
import nl.jacobras.cloudbridge.model.UserInfo
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ServicesList(
    connectedServices: Map<CloudService, UserInfo?>,
    selectedService: CloudService?,
    onServiceClick: (CloudService) -> Unit,
    onAddClick: (AvailableService) -> Unit,
    onFinishAuthClick: (AvailableService) -> Unit
) {
    Column {
        SectionHeader("Connected services")
        for ((service, userInfo) in connectedServices) {
            Service(
                service = service,
                userInfo = userInfo,
                selected = selectedService == service,
                onClick = { onServiceClick(service) }
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader("Available services")
        for (service in AvailableService.entries) {
            AvailableService(
                service = service,
                onClick = { onAddClick(service) },
                onFinishAuthClick = { onFinishAuthClick(service) }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun Service(
    service: CloudService,
    userInfo: UserInfo?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo(service.logo)
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildString {
                append(service.displayName)

                if (userInfo?.emailAddress != null) {
                    append(" (${userInfo.emailAddress})")
                }
            },
            fontWeight = if (selected) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            }
        )
    }
}

@Composable
private fun AvailableService(
    service: AvailableService,
    onClick: () -> Unit,
    onFinishAuthClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo(service.logo)
        Spacer(Modifier.width(8.dp))
        Text(modifier = Modifier.weight(1f), text = service.displayName)

        if (isWeb && service != AvailableService.WebDav) {
            // On web the sign-in flow navigates away and back, so it has to be finished
            // manually after returning.
            TextButton(onClick = onFinishAuthClick) {
                Text("Finish auth")
            }
        }
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Default.Add,
            contentDescription = "Add"
        )
    }
}

@Composable
private fun Logo(logo: ServiceLogo) {
    when (logo) {
        is ServiceLogo.Drawable -> Image(
            modifier = Modifier.size(32.dp),
            painter = painterResource(logo.resource),
            contentDescription = null
        )
        is ServiceLogo.Vector -> Icon(
            modifier = Modifier.size(32.dp),
            imageVector = logo.image,
            contentDescription = null
        )
    }
}