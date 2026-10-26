package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.foundation.layout.Box
import com.vincenthzr.locationspoofer.ui.theme.animateListItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.vincenthzr.locationspoofer.data.model.SavedLocation
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.AppBottomSheet
import com.vincenthzr.locationspoofer.ui.components.EmptyState

@Composable
fun SavedLocationsDialog(
    savedLocations: List<SavedLocation>,
    onDismiss: () -> Unit,
    onSelect: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit
) {
    AppBottomSheet(
        title = stringResource(R.string.saved_locations),
        // 列表为空时由下方空状态说明，标题下不再重复同一句话
        subtitle = if (savedLocations.isEmpty()) null else stringResource(R.string.saved_locations_count_format, savedLocations.size),
        onDismiss = onDismiss
    ) {
        if (savedLocations.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.StarOutline,
                title = stringResource(R.string.no_saved_locations),
                description = stringResource(R.string.map_save_location_hint)
            )
        } else {
            LazyColumn {
                items(savedLocations, key = { "${it.name}_${it.lat}_${it.lng}" }) { loc ->
                    Box(Modifier.animateListItem(this)) {
                        SavedLocationRow(loc, onSelect = { onSelect(loc) }, onDelete = { onDelete(loc) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedLocationRow(loc: SavedLocation, onSelect: () -> Unit, onDelete: () -> Unit) {
                val coordText = "${
                    String.format(java.util.Locale.US, "%.6f", loc.lat)
                }, ${
                    String.format(java.util.Locale.US, "%.6f", loc.lng)
                }"
                // 没有备注/地名的收藏点，名字本身就是坐标，
                // 再渲染一行坐标就是上下两行雷同（issue #56）。
                // 去掉括号与空格后归一化比较，一致就只显示一行。
                val normalize = { text: String ->
                    text.replace("(", "").replace(")", "")
                        .replace("（", "").replace("）", "")
                        .replace(" ", "")
                }
                val nameIsCoordinate =
                    normalize(loc.name) == normalize(coordText) ||
                            normalize(loc.name) == normalize(
                        "${String.format(java.util.Locale.US, "%.5f", loc.lat)}, ${
                            String.format(java.util.Locale.US, "%.5f", loc.lng)
                        }"
                    )

    ListItem(
        modifier = Modifier.clickable(onClick = onSelect),
        leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
        headlineContent = { Text(if (nameIsCoordinate) coordText else loc.name) },
        supportingContent = if (!nameIsCoordinate) {
            { Text(coordText) }
        } else null,
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
