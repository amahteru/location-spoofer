package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.screen.tabs.location.SavedLocationItemRow
import androidx.compose.foundation.layout.Box
import io.github.amahteru.locationspoofer.ui.theme.animateListItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onDelete: (SavedLocation) -> Unit,
    onRename: (SavedLocation) -> Unit
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
                items(savedLocations, key = { "${it.lat}_${it.lng}" }) { loc ->
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
