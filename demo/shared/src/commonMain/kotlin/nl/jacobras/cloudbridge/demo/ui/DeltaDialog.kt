package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.delta.DeletedDeltaChange
import nl.jacobras.cloudbridge.delta.DeltaChange
import nl.jacobras.cloudbridge.delta.DeltaCursor
import nl.jacobras.cloudbridge.delta.DeltaResult
import nl.jacobras.cloudbridge.delta.UpdatedDeltaChange

/**
 * Dialog for testing delta calls: fetch the changes since [cursor], inspect them
 * and then manually move the cursor forward.
 */
@Composable
internal fun DeltaDialog(
    cursor: DeltaCursor,
    result: DeltaResult?,
    error: String,
    onFetch: () -> Unit,
    onAdvance: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onFetch) { Text("Fetch") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Delta",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LabeledCursor(label = "Current cursor", cursor = cursor) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }

                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (result == null) {
                    Text(
                        text = "Press Fetch to get the changes since this cursor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ResultCard(result = result, onAdvance = onAdvance)
                }
            }
        }
    )
}

@Composable
private fun ResultCard(result: DeltaResult, onAdvance: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Result",
                style = MaterialTheme.typography.titleSmall
            )
            LabeledCursor(label = "New cursor", cursor = result.newCursor) {
                FilledTonalButton(onClick = onAdvance) { Text("Move cursor forward") }
            }
            HorizontalDivider()
            Text(
                text = "Changes (${result.changes.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (result.changes.isEmpty()) {
                Text(
                    text = "No changes",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.changes.forEach { ChangeBullet(it) }
                }
            }
        }
    }
}

@Composable
private fun LabeledCursor(
    label: String,
    cursor: DeltaCursor,
    action: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = cursor.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            action()
        }
    }
}

@Composable
private fun ChangeBullet(change: DeltaChange) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium
        )
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (change) {
                        is UpdatedDeltaChange -> "Updated"
                        is DeletedDeltaChange -> "Deleted"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when (change) {
                        is UpdatedDeltaChange -> MaterialTheme.colorScheme.primary
                        is DeletedDeltaChange -> MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = change.path.toString(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (change is UpdatedDeltaChange) {
                Text(
                    text = "${change.revision} · ${change.modified}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
