package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.viewmodel.*
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun CollectionRouteControls(viewModel: MainViewModel, state: AppState, map: AppMapController?, modifier: Modifier = Modifier) {
    val busy = state.isContinuousScanning || state.isStoppingCollection
    Surface(
        modifier = modifier.fillMaxWidth(0.92f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            if (!busy) {
                // 采点 / 采路线 两种模式：M3 分段按钮
                val modes = listOf(false to R.string.collection_point_mode, true to R.string.collection_route_mode)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (route, label) ->
                        SegmentedButton(
                            selected = state.isRouteCollection == route,
                            onClick = { viewModel.setRouteCollection(route) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                        ) {
                            Text(stringResource(label), maxLines = 1)
                        }
                    }
                }
            }
            if (state.isRouteCollection) {
                Text(stringResource(if (state.isDrawingCollectionRoute) R.string.collection_route_draw_hint else R.string.collection_route_collect_hint), style = MaterialTheme.typography.bodySmall)
                if (state.isDrawingCollectionRoute) {
                    Text(stringResource(R.string.selected_waypoints_count, state.collectionRoutePoints.size), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        TextButton(enabled = state.collectionRoutePoints.isNotEmpty(), onClick = viewModel::undoCollectionRoutePoint) {
                            Text(stringResource(R.string.collection_route_undo))
                        }
                        Button(modifier = Modifier.weight(1f), onClick = {
                            val lat = map?.cameraTargetLat ?: state.latitudeInput.toDoubleOrNull()
                            val lng = map?.cameraTargetLng ?: state.longitudeInput.toDoubleOrNull()
                            if (lat != null && lng != null) viewModel.addCollectionRoutePoint(lat, lng)
                        }) { Text(stringResource(R.string.collection_route_add_point)) }
                        TextButton(enabled = state.collectionRoutePoints.size >= 2, onClick = viewModel::finishCollectionRouteDrawing) {
                            Text(stringResource(R.string.collection_route_done))
                        }
                    }
                } else if (!busy) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.selected_waypoints_count, state.collectionRoutePoints.size), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::drawCollectionRoute) {
                            Text(stringResource(R.string.collection_route_edit_drawing))
                        }
                    }
                }
            }
            if (!state.isRouteCollection || !state.isDrawingCollectionRoute) {
                Button(
                    enabled = !state.isStoppingCollection,
                    onClick = viewModel::toggleContinuousScanning,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = if (state.isContinuousScanning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else ButtonDefaults.buttonColors()
                ) {
                    Text(stringResource(when {
                        state.isStoppingCollection -> R.string.collection_stopping
                        state.isContinuousScanning -> R.string.stop_collection
                        state.isRouteCollection -> R.string.collection_route_start
                        else -> R.string.start_collection
                    }))
                }
            }
        }
    }
}
