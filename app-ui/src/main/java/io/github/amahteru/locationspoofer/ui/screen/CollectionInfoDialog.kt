package io.github.amahteru.locationspoofer.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.saveCollectionInfo
import io.github.amahteru.locationspoofer.viewmodel.skipAllCollectionInfo
import io.github.amahteru.locationspoofer.viewmodel.skipCollectionInfo
import io.github.amahteru.locationspoofer.viewmodel.saveCollectionRouteInfo
import io.github.amahteru.locationspoofer.viewmodel.skipCollectionRouteInfo
import java.util.Locale
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun CollectionInfoDialogHost(viewModel: MainViewModel, uiState: AppState) {
    val route = uiState.pendingCollectionRoute
    if (route != null) {
        CollectionRouteInfoDialog(
            route, uiState.isSavingCollectionInfo,
            onDismiss = viewModel::skipCollectionRouteInfo,
            onSave = { name, remark -> viewModel.saveCollectionRouteInfo(route.id, name, remark) }
        )
        return
    }
    val record = uiState.pendingCollectionLocations.firstOrNull() ?: return
    val remaining = uiState.pendingCollectionLocations.size
    CollectionDetailsDialog(
        key = "location-${record.id}",
        title = stringResource(R.string.collection_info_title),
        nameLabel = stringResource(R.string.collection_location_name),
        namePlaceholder = stringResource(R.string.set_place_name_hint),
        initialName = record.placeName,
        initialRemark = record.remark,
        saving = uiState.isSavingCollectionInfo,
        nameRequired = false,
        description = stringResource(R.string.collection_info_description),
        dismissLabel = stringResource(R.string.collection_info_skip),
        onDismiss = viewModel::skipCollectionInfo,
        onSkipAll = if (remaining > 1) viewModel::skipAllCollectionInfo else null,
        onSave = { name, remark -> viewModel.saveCollectionInfo(record.id, name, remark) }
    ) {
        if (remaining > 1) Text(stringResource(R.string.collection_info_remaining, remaining))
        Text(
            String.format(Locale.US, "%.6f, %.6f", record.lat, record.lng),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CollectionRouteInfoDialog(route: CollectionRouteRecord, saving: Boolean, editing: Boolean = false, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    CollectionDetailsDialog(
        key = "route-${route.id}",
        title = stringResource(R.string.collection_route_details),
        nameLabel = stringResource(R.string.collection_route_name),
        namePlaceholder = null,
        initialName = route.name,
        initialRemark = route.remark,
        saving = saving,
        nameRequired = true,
        description = if (editing) null else stringResource(R.string.collection_info_description),
        dismissLabel = stringResource(if (editing) R.string.cancel else R.string.collection_info_skip),
        onDismiss = onDismiss,
        onSave = onSave
    )
}

@Composable
private fun CollectionDetailsDialog(
    key: String,
    title: String,
    nameLabel: String,
    namePlaceholder: String?,
    initialName: String,
    initialRemark: String,
    saving: Boolean,
    nameRequired: Boolean,
    description: String?,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onSkipAll: (() -> Unit)? = null,
    details: @Composable ColumnScope.() -> Unit = {}
) {
    var name by rememberSaveable(key) { mutableStateOf(initialName) }
    var remark by rememberSaveable(key) { mutableStateOf(initialRemark) }

    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                if (description != null) Text(description)
                details()
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    placeholder = namePlaceholder?.let { { Text(it) } },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text(stringResource(R.string.remark_note)) },
                    placeholder = { Text(stringResource(R.string.add_remark_hint)) },
                    minLines = 2,
                    maxLines = 3,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            Row {
                onSkipAll?.let {
                    TextButton(enabled = !saving, onClick = it) {
                        Text(stringResource(R.string.collection_info_skip_all))
                    }
                }
                TextButton(enabled = !saving, onClick = onDismiss) {
                    Text(dismissLabel)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && (!nameRequired || name.isNotBlank()),
                onClick = { onSave(name, remark) }
            ) {
                Text(stringResource(if (saving) R.string.collection_info_saving else R.string.save))
            }
        }
    )
}
