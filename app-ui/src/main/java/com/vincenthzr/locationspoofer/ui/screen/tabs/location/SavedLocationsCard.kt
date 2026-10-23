package com.vincenthzr.locationspoofer.ui.screen.tabs.location

import com.vincenthzr.locationspoofer.ui.theme.AppMotion
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.SavedLocation
import java.util.Locale
import androidx.compose.foundation.clickable

// 动态可滑动高度的收藏地点卡片（支持无限数量坐标平滑滚动，并支持抽拉下沉）
@Composable
fun SavedLocationsCard(
    modifier: Modifier = Modifier,
    savedLocations: List<SavedLocation>,
    panelState: LocationPanelState,
    onSelect: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit,
    onToggleExpand: () -> Unit,
    onOpenManageDialog: () -> Unit,
    dragGestureModifier: Modifier = Modifier
) {
    // 与上方坐标卡片同一层级：surfaceContainerLow + 一级阴影的 extraLarge 浮层
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            // 卡片头部（支持拖拽展开/折叠）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp)
                    .then(dragGestureModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.saved_locations_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text("${savedLocations.size}")
                }

                Spacer(Modifier.weight(1f))

                if (savedLocations.isNotEmpty()) {
                    TextButton(onClick = onOpenManageDialog) {
                        Text(stringResource(R.string.saved_locations))
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (panelState == LocationPanelState.EXPANDED) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null
                    )
                }
            }

            // 卡片主体内容（海量坐标平滑滚动，限制最大高度避免溢出屏幕）
            if (savedLocations.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .then(dragGestureModifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Icon(
                        Icons.Rounded.StarOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.no_saved_locations_card_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(
                        items = savedLocations,
                        key = { "${it.name}_${it.lat}_${it.lng}" }
                    ) { loc ->
                        Box(Modifier.animateItem(fadeInSpec = AppMotion.defaultEffectsSpec(), placementSpec = AppMotion.defaultSpatialSpec(), fadeOutSpec = AppMotion.fastEffectsSpec())) {
                            SavedLocationItemRow(
                                location = loc,
                                onSelect = { onSelect(loc) },
                                onDelete = { onDelete(loc) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedLocationItemRow(
    location: SavedLocation,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onSelect),
        leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
        headlineContent = { Text(location.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("${String.format(Locale.US, "%.5f", location.lat)}, ${String.format(Locale.US, "%.5f", location.lng)}")
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete_saved_location_short))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
