package io.github.amahteru.locationspoofer.ui.screen.tabs.location

import androidx.compose.material.icons.rounded.Edit
import io.github.amahteru.locationspoofer.ui.theme.animateListItem
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
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.SavedLocation
import java.util.Locale
import androidx.compose.foundation.clickable
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun SavedLocationsCard(
    modifier: Modifier = Modifier,
    savedLocations: List<SavedLocation>,
    panelState: LocationPanelState,
    onSelect: (SavedLocation) -> Unit,
    onRename: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit,
    onToggleExpand: () -> Unit,
    onOpenManageDialog: () -> Unit,
    dragGestureModifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.lg, end = AppSpacing.xs)
                    .then(dragGestureModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = stringResource(R.string.saved_locations_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(AppSpacing.sm))
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

            if (savedLocations.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg)
                        .then(dragGestureModifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.CenterHorizontally)
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
                        key = { "${it.lat}_${it.lng}" }
                    ) { loc ->
                        Box(Modifier.animateListItem(this)) {
                            SavedLocationItemRow(
                                location = loc,
                                onSelect = { onSelect(loc) },
                                onRename = { onRename(loc) },
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
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val coordinates = String.format(Locale.US, "%.5f, %.5f", location.lat, location.lng)
    ListItem(
        modifier = Modifier.clickable(onClick = onSelect),
        leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
        headlineContent = { Text(location.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (location.name == "($coordinates)") null else {
            { Text(coordinates) }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.rename))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
