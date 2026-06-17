package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.CloudService
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ServicesList(
    services: List<ServiceWithInfo>,
    selectedService: CloudService?,
    onClick: (ServiceWithInfo) -> Unit
) {
    Column {
        for (info in services) {
            Service(
                service = info,
                selected = selectedService == info.service,
                onClick = { onClick(info) }
            )
        }
    }
}

@Composable
private fun Service(
    service: ServiceWithInfo,
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
        Image(
            modifier = Modifier.size(32.dp),
            painter = painterResource(service.logo),
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildString {
                append(service.name)

                if (service.userName.isNotBlank()) {
                    append(" (${service.userName})")
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