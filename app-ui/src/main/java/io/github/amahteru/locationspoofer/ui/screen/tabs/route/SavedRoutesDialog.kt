package io.github.amahteru.locationspoofer.ui.screen.tabs.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.components.AppBottomSheet
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.EmptyState
import io.github.amahteru.locationspoofer.ui.components.STREET_ZOOM
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.deleteSavedRoute
import io.github.amahteru.locationspoofer.viewmodel.loadSavedRoute

@Composable
fun SavedRoutesDialog(
    uiState: AppState,
    viewModel: MainViewModel,
    mapController: AppMapController?,
    bottomActionHeightDp: Dp,
    bottomBarHeight: Dp,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current

    AppBottomSheet(
        title = stringResource(R.string.route_library),
        subtitle = if (uiState.savedRoutes.isEmpty()) null else stringResource(R.string.total_saved_routes, uiState.savedRoutes.size),
        onDismiss = onDismiss
    ) {
        if (uiState.savedRoutes.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Route,
                title = stringResource(R.string.no_saved_routes),
                description = stringResource(R.string.add_points_save_hint)
            )
        } else {
            LazyColumn {
                items(uiState.savedRoutes) { route ->
                    ListItem(
                        modifier = Modifier.clickable {
                            viewModel.loadSavedRoute(route)
                            if (route.points.size >= 2) {
                                mapController?.fitRoute(route.points, density, bottomActionHeightDp, bottomBarHeight)
                            } else {
                                route.points.firstOrNull()?.let { firstPoint ->
                                    mapController?.animateCamera(
                                        firstPoint.lat,
                                        firstPoint.lng,
                                        STREET_ZOOM
                                    )
                                }
                            }
                            onDismiss()
                        },
                        leadingContent = { Icon(Icons.Rounded.Route, contentDescription = null) },
                        headlineContent = { Text(route.name) },
                        supportingContent = { Text(stringResource(R.string.route_nodes_count, route.points.size)) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteSavedRoute(route) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
