package io.github.amahteru.locationspoofer.ui.components

import io.github.amahteru.locationspoofer.data.model.isAt
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import io.github.amahteru.locationspoofer.ui.theme.animateListItem
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.data.model.SavedLocation
import io.github.amahteru.locationspoofer.data.model.SavedRoute
import io.github.amahteru.locationspoofer.data.model.routePoints
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun LocalEnvironmentDataDialog(
    dataList: List<CompleteLocation>,
    isLoading: Boolean,
    savedLocations: List<SavedLocation>,
    onSelectPoint: (item: CompleteLocation) -> Unit,
    onFavorite: (item: CompleteLocation) -> Unit,
    onImportClick: () -> Unit,
    onDismiss: () -> Unit,
    collectionRoutes: List<io.github.amahteru.locationspoofer.data.db.CompleteCollectionRoute> = emptyList(),
    onSelectRoute: (io.github.amahteru.locationspoofer.data.db.CompleteCollectionRoute) -> Unit = {},
    savedRoutes: List<SavedRoute> = emptyList(),
    onFavoriteRoute: (io.github.amahteru.locationspoofer.data.db.CompleteCollectionRoute) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    val favoritedRoutes = remember(savedRoutes) { savedRoutes.mapTo(HashSet()) { it.points } }

    val filteredList = remember(dataList, searchQuery) {
        if (searchQuery.isBlank()) {
            dataList
        } else {
            val query = searchQuery.trim().lowercase()
            dataList.filter { item ->
                item.location.remark.lowercase().contains(query) ||
                        item.location.placeName.lowercase().contains(query) ||
                        item.location.lat.toString().contains(query) ||
                        item.location.lng.toString().contains(query) ||
                        "${item.location.lat},${item.location.lng}".contains(query)
            }
        }
    }

    val filteredRoutes = remember(collectionRoutes, searchQuery) {
        val query = searchQuery.trim()
        collectionRoutes.filter { query.isBlank() || it.route.name.contains(query, true) || it.route.remark.contains(query, true) }
    }

    AppBottomSheet(
        title = stringResource(R.string.local_collected_datasource),
        subtitle = if (dataList.isEmpty() && collectionRoutes.isEmpty()) stringResource(R.string.no_local_env_data) else stringResource(R.string.local_collection_records_count, dataList.size, collectionRoutes.size),
        onDismiss = onDismiss,
        skipPartiallyExpanded = true,
        actions = {
            if (dataList.isNotEmpty() || collectionRoutes.isNotEmpty()) {
                IconButton(onClick = onImportClick) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.import_external_data_json))
                }
            }
        }
    ) {
        if (dataList.isNotEmpty() || collectionRoutes.isNotEmpty()) {
            AppSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.search_remark_coord_hint),
                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
            )
        }

    val listState = when {
        isLoading -> 0
        dataList.isEmpty() && collectionRoutes.isEmpty() -> 1
        filteredList.isEmpty() && filteredRoutes.isEmpty() -> 2
        else -> 3
    }
    AnimatedContent(
        targetState = listState,
        transitionSpec = { fadeIn(AppMotion.defaultEffectsSpec()) togetherWith fadeOut(AppMotion.fastEffectsSpec()) },
        modifier = Modifier.weight(1f, fill = false),
        label = "local_data_list_state"
    ) { state ->
        when (state) {
            0 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(
                        text = stringResource(R.string.no_local_env_data),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = stringResource(R.string.scan_or_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = AppSpacing.lg)
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    FilledTonalButton(
                        onClick = onImportClick,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.import_external_data_json))
                    }
                }
            }
            2 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_matching_collected_points),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(filteredRoutes, key = { "collection-route-${it.route.id}" }) { route ->
                        Box(Modifier.animateListItem(this)) {
                            CollectedRouteCard(route, onClick = { onSelectRoute(route) }) {
                                FavoriteToggle(route.route.routePoints() in favoritedRoutes) { onFavoriteRoute(route) }
                            }
                        }
                    }
                    items(filteredList, key = { it.location.id }) { item ->
                        Box(Modifier.animateListItem(this)) {
                            CollectedLocationCard(
                                item,
                                onClick = {
                                    onSelectPoint(item)
                                    onDismiss()
                                }
                            ) {
                                FavoriteToggle(savedLocations.any { it.isAt(item.location.lat, item.location.lng) }) { onFavorite(item) }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun FavoriteToggle(favorited: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = favorited, onCheckedChange = { onToggle() }) {
        Icon(
            imageVector = if (favorited) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = stringResource(
                if (favorited) R.string.remove_from_favorites else R.string.add_to_favorites
            )
        )
    }
}
