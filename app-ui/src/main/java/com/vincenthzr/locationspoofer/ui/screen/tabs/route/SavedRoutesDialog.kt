package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.components.AppBottomSheet
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.EmptyState
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.deleteSavedRoute
import com.vincenthzr.locationspoofer.viewmodel.loadSavedRoute

@Composable
fun SavedRoutesDialog(
    uiState: AppState,
    viewModel: MainViewModel,
    mapController: AppMapController?,
    bottomActionHeightDp: Dp,
    bottomBarHeight: Dp,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    AppBottomSheet(
        title = stringResource(R.string.route_library),
        subtitle = if (uiState.savedRoutes.isEmpty()) stringResource(R.string.no_saved_routes) else stringResource(R.string.total_saved_routes, uiState.savedRoutes.size),
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
                                val padLeft = with(density) { 36.dp.roundToPx() }
                                val padTop = with(density) { 80.dp.roundToPx() }
                                val padRight = with(density) { 36.dp.roundToPx() }
                                val effectiveBottomDp =
                                    if (bottomActionHeightDp > 0.dp) bottomActionHeightDp + bottomBarHeight + 24.dp else bottomBarHeight + 160.dp
                                val padBottom =
                                    with(density) { effectiveBottomDp.roundToPx() }
                                mapController?.fitBounds(
                                    points = route.points.map { Pair(it.lat, it.lng) },
                                    paddingLeft = padLeft,
                                    paddingTop = padTop,
                                    paddingRight = padRight,
                                    paddingBottom = padBottom
                                )
                            } else {
                                route.points.firstOrNull()?.let { firstPoint ->
                                    mapController?.animateCamera(
                                        firstPoint.lat,
                                        firstPoint.lng,
                                        17.5f
                                    )
                                }
                            }
                            onDismiss()
                            Toast.makeText(
                                context,
                                context.getString(R.string.loaded_route, route.name),
                                Toast.LENGTH_SHORT
                            ).show()
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
