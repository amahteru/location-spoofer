package io.github.amahteru.locationspoofer.ui.screen

import androidx.compose.foundation.layout.Box
import io.github.amahteru.locationspoofer.ui.theme.animateListItem
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
import io.github.amahteru.locationspoofer.data.model.SavedLocation
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.components.AppBottomSheet
import io.github.amahteru.locationspoofer.ui.components.EmptyState

@Composable
fun SavedLocationsDialog(
    savedLocations: List<SavedLocation>,
    onDismiss: () -> Unit,
    onSelect: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit
) {
    AppBottomSheet(
        title = stringResource(R.string.saved_locations),
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
