package com.vincenthzr.locationspoofer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.ImportExportCounts
import com.vincenthzr.locationspoofer.data.model.ImportExportSelection
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role

/**
 * 导入 / 导出共用的分类选择对话框。
 * 导出时 counts 是"当前设备上有多少"，导入时是"文件里有多少"；数量为 0 的分类禁用。
 */
@Composable
fun ImportExportSelectionDialog(
    isExport: Boolean,
    counts: ImportExportCounts,
    onConfirm: (ImportExportSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var selection by remember { mutableStateOf(ImportExportSelection()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ImportExport, contentDescription = null) },
        title = {
            Text(stringResource(if (isExport) R.string.export_select_title else R.string.import_select_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (isExport) R.string.export_select_desc else R.string.import_select_desc))
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SelectionRow(
                        label = stringResource(R.string.category_collected_data),
                        count = counts.locations,
                        checked = selection.locations,
                        onCheckedChange = { selection = selection.copy(locations = it) }
                    )
                    SelectionRow(
                        label = stringResource(R.string.category_saved_locations),
                        count = counts.savedLocations,
                        checked = selection.savedLocations,
                        onCheckedChange = { selection = selection.copy(savedLocations = it) }
                    )
                    SelectionRow(
                        label = stringResource(R.string.category_saved_routes),
                        count = counts.savedRoutes,
                        checked = selection.savedRoutes,
                        onCheckedChange = { selection = selection.copy(savedRoutes = it) }
                    )
                    SelectionRow(
                        label = stringResource(R.string.category_app_coordinates),
                        count = counts.appCoordinateSystems,
                        checked = selection.appCoordinateSystems,
                        onCheckedChange = { selection = selection.copy(appCoordinateSystems = it) }
                    )
                    SelectionRow(
                        label = stringResource(R.string.category_app_settings),
                        count = counts.settings,
                        showCount = false,
                        checked = selection.settings,
                        onCheckedChange = { selection = selection.copy(settings = it) }
                    )
                    SelectionRow(
                        label = stringResource(R.string.category_api_keys),
                        count = counts.apiKeys,
                        warning = stringResource(R.string.category_api_keys_warning),
                        checked = selection.apiKeys,
                        onCheckedChange = { selection = selection.copy(apiKeys = it) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selection) }, enabled = selection.hasAny) {
                Text(stringResource(if (isExport) R.string.export_action else R.string.import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SelectionRow(
    label: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    warning: String? = null,
    showCount: Boolean = true
) {
    val enabled = count > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(
                value = checked && enabled,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked && enabled,
            onCheckedChange = null,
            enabled = enabled
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (showCount && enabled) {
                    stringResource(R.string.category_with_count, label, count)
                } else label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (warning != null) {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            } else if (!enabled) {
                Text(
                    text = stringResource(R.string.category_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
