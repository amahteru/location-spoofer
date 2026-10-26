package com.vincenthzr.locationspoofer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppMapType
import com.vincenthzr.locationspoofer.data.model.MapEngine
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

/**
 * 图层与地图引擎选择：M3 Modal Bottom Sheet，图层用分段按钮、引擎用单选列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTypeDialog(
    currentMapType: AppMapType,
    onMapTypeSelected: (AppMapType) -> Unit,
    currentMapEngine: MapEngine,
    onMapEngineSelected: (MapEngine) -> Unit,
    onDismiss: () -> Unit
) {
    val mapTypes = listOf(
        Triple(AppMapType.NORMAL, Icons.Rounded.Map, R.string.map_type_standard),
        Triple(AppMapType.SATELLITE, Icons.Rounded.SatelliteAlt, R.string.map_type_satellite),
        Triple(AppMapType.MAP_3D, Icons.Rounded.ViewInAr, R.string.map_type_3d)
    )
    val engines = listOf(
        Triple(MapEngine.AUTO, Icons.Rounded.AutoMode, R.string.auto_match),
        Triple(MapEngine.AMAP, Icons.Rounded.Navigation, R.string.map_engine_amap),
        Triple(MapEngine.BAIDU, Icons.Rounded.Explore, R.string.map_engine_baidu),
        Triple(MapEngine.GOOGLE, Icons.Rounded.Public, R.string.map_engine_google)
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = AppSpacing.lg)) {
            Text(
                text = stringResource(R.string.layers_and_engine_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = AppSpacing.screenMargin)
            )
            Text(
                text = stringResource(R.string.layers_and_engine_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.screenMargin, vertical = AppSpacing.xs)
            )

            SheetSectionHeader(stringResource(R.string.map_layers))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screenMargin)
            ) {
                mapTypes.forEachIndexed { index, (type, icon, label) ->
                    SegmentedButton(
                        selected = currentMapType == type,
                        onClick = { onMapTypeSelected(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, mapTypes.size),
                        icon = { SegmentedButtonDefaults.Icon(active = currentMapType == type) { Icon(icon, null, Modifier.size(18.dp)) } }
                    ) {
                        Text(stringResource(label), maxLines = 1)
                    }
                }
            }

            SheetSectionHeader(stringResource(R.string.map_engine_service))
            Column(modifier = Modifier.selectableGroup()) {
                engines.forEach { (engine, icon, label) ->
                    val selected = currentMapEngine == engine
                    ListItem(
                        headlineContent = { Text(stringResource(label)) },
                        leadingContent = { Icon(icon, contentDescription = null) },
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .padding(horizontal = AppSpacing.sm)
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onMapEngineSelected(engine) }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = AppSpacing.screenMargin, end = AppSpacing.screenMargin, top = AppSpacing.xl, bottom = AppSpacing.sm)
    )
}
