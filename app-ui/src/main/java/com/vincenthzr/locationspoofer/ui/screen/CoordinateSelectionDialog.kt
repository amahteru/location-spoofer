package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.AppInfoItem
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.StatusLabel

@Composable
fun CoordinateSelectionDialog(
    appInfo: AppInfoItem,
    currentSystem: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var selectedOption by remember { mutableStateOf(currentSystem) }

    val options = listOf(
        CoordinateOption(
            key = "AUTO",
            name = stringResource(R.string.auto_coord_algo),
            tag = stringResource(R.string.default_recommended),
            desc = stringResource(R.string.auto_coord_desc)
        ),
        CoordinateOption(
            key = "WGS-84",
            name = stringResource(R.string.wgs84_title),
            tag = null,
            desc = stringResource(R.string.wgs84_desc)
        ),
        CoordinateOption(
            key = "GCJ-02",
            name = stringResource(R.string.gcj02_title),
            tag = null,
            desc = stringResource(R.string.gcj02_desc)
        ),
        CoordinateOption(
            key = "BD-09",
            name = stringResource(R.string.bd09_title),
            tag = null,
            desc = stringResource(R.string.bd09_desc)
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIconImage(packageName = appInfo.packageName, modifier = Modifier.size(40.dp)) },
        title = { Text(appInfo.appName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.select_coord_algo_title),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                // 坐标系单选列表
                Column(modifier = Modifier.selectableGroup()) {
                    options.forEach { option ->
                        val isSelected = option.key == selectedOption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { selectedOption = option.key }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = option.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (option.tag != null) {
                                        StatusLabel(text = option.tag)
                                    }
                                }
                                Text(
                                    text = option.desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selectedOption) }) {
                Text(stringResource(R.string.app_coordinate_setting))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private data class CoordinateOption(
    val key: String,
    val name: String,
    val tag: String?,
    val desc: String
)
