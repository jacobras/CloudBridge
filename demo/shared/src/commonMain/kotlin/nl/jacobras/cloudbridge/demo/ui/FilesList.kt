package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.jacobras.cloudbridge.model.CloudItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilesList(
    serviceWithInfo: ServiceWithInfo,
    files: List<CloudItem>,
    onBackClick: () -> Unit,
    onItemClick: (CloudItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        for (item in files) {
            FileRow(
                item = item,
                onClick = { onItemClick(item) }
            )
            if (item != files.last()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}