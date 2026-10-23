package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import com.vincenthzr.locationspoofer.ui.components.NavigationCard

@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun AppCoordinateConfigCard(onClick: () -> Unit) {
    NavigationCard(
        icon = Icons.Rounded.Extension,
        title = stringResource(R.string.config_app_coordinate),
        summary = stringResource(R.string.config_app_coordinate_desc),
        tone = LabelTone.Primary,
        onClick = onClick
    )
}

@Composable
fun SystemHookAppsConfigCard(uiState: AppState, onClick: () -> Unit) {
    val statusDesc = if (uiState.isSystemHookGlobalMode) {
        stringResource(R.string.system_hook_global_mode_title) + "（所有应用生效）"
    } else if (uiState.systemHookPackages.isNotEmpty()) {
        stringResource(R.string.selected_count_format, uiState.systemHookPackages.size)
    } else {
        stringResource(R.string.system_hook_apps_card_desc)
    }
    NavigationCard(
        icon = Icons.Rounded.Shield,
        title = stringResource(R.string.system_hook_apps_title),
        summary = statusDesc,
        tone = LabelTone.Warning,
        onClick = onClick
    )
}

@Composable
fun ScannerMapCard(
    uiState: AppState,
    onClick: () -> Unit
) {
    val statusText = if (uiState.isContinuousScanning) {
        stringResource(R.string.scanning_reference_points, uiState.environmentRecordCount)
    } else {
        stringResource(R.string.view_heatmap_start_scan)
    }
    NavigationCard(
        icon = Icons.Rounded.Map,
        title = stringResource(R.string.env_map_scan),
        summary = statusText,
        tone = LabelTone.Success,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isContinuousScanning) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    )
}

@Composable
fun ManageDataCard(onClick: () -> Unit) {
    NavigationCard(
        icon = Icons.Rounded.FolderShared,
        title = stringResource(R.string.title_manage_data),
        summary = stringResource(R.string.manage_collected_data_desc),
        tone = LabelTone.Tertiary,
        onClick = onClick
    )
}

@Composable
fun ImportExportDataCard(onImportClick: () -> Unit, onExportClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { com.vincenthzr.locationspoofer.ui.components.ToneIcon(Icons.Rounded.ImportExport, LabelTone.Primary) },
            headlineContent = { Text(stringResource(R.string.env_data_sharing)) },
            supportingContent = { Text(stringResource(R.string.env_data_sharing_desc)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onImportClick) {
                Text(stringResource(R.string.import_data))
            }
            FilledTonalButton(onClick = onExportClick) {
                Text(stringResource(R.string.export_data))
            }
        }
    }
}

@Composable
fun FooterLinks() {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/amahteru/location-spoofer") },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.brand_github))
        }
        OutlinedButton(
            onClick = { uriHandler.openUri("https://t.me/+CsxZGItXdW40ZWVl") },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_telegram),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.brand_telegram))
        }
    }
}
