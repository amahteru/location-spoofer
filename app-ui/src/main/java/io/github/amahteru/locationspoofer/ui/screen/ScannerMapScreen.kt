package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import io.github.amahteru.locationspoofer.ui.screen.tabs.location.MapControlButton
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import io.github.amahteru.locationspoofer.ui.components.CollectionRouteMapHelper
import io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.AppMapView
import io.github.amahteru.locationspoofer.ui.components.MapTypeDialog
import io.github.amahteru.locationspoofer.ui.components.MapCoverageHelper
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.fetchCurrentLocation
import io.github.amahteru.locationspoofer.viewmodel.isDomesticEnvironment
import io.github.amahteru.locationspoofer.viewmodel.setMapEngine
import io.github.amahteru.locationspoofer.viewmodel.setMapType
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun ScannerMapScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    var showMapTypeDialog by remember { mutableStateOf(false) }
    val environmentLocations by viewModel.environmentLocations.collectAsState()
    val collectionRoutes by viewModel.collectionRoutes.collectAsState()
    val locations = remember(environmentLocations) { environmentLocations.map { it.location }.filter { it.collectionRouteId == null } }

    LaunchedEffect(Unit) {
        viewModel.fetchCurrentLocation(context) { lat, lng ->
            mapController?.animateCamera(lat, lng, 16.5f)
        }
    }

    LaunchedEffect(mapController, uiState.mapType) {
        mapController?.setMapType(uiState.mapType)
    }

    LaunchedEffect(mapController, locations, collectionRoutes, uiState.collectionRoutePoints) {
        val controller = mapController ?: return@LaunchedEffect
        val currentLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
        val currentLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
        controller.clear()

        MapCoverageHelper.drawCoverage(controller, locations, currentLat, currentLng)

        collectionRoutes.take(10).forEach { CollectionRouteMapHelper.draw(controller, it.route, 12) }
        if (uiState.collectionRoutePoints.size >= 2) {
            CollectionRouteMapHelper.draw(controller, CollectionRouteRecord(
                pointsJson = kotlinx.serialization.json.Json.encodeToString(uiState.collectionRoutePoints)), 60)
        } else uiState.collectionRoutePoints.firstOrNull()?.let {
            controller.addMarker(it.lat, it.lng, "", io.github.amahteru.locationspoofer.ui.components.MarkerType.GREEN)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppMapView(
            mapEngine = uiState.mapEngine,
            isDomestic = viewModel.isDomesticEnvironment(),
            modifier = Modifier.fillMaxSize(),
            onMapReady = { controller ->
                mapController = controller
                controller.disableUiControls()
                val currentLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                val currentLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                controller.moveCamera(currentLat, currentLng, 16.5f)
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MapControlButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onClose,
                contentDescription = stringResource(R.string.back)
            )

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.isContinuousScanning) Icons.Rounded.Radar else Icons.Rounded.PauseCircle,
                        contentDescription = null,
                        tint = if (uiState.isContinuousScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    Text(
                        text = if (uiState.isStoppingCollection) {
                            stringResource(R.string.collection_stopping)
                        } else if (uiState.isContinuousScanning) {
                            stringResource(
                                R.string.scanning_status_active,
                                uiState.environmentRecordCount
                            )
                        } else {
                            stringResource(R.string.scanning_status_inactive)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isContinuousScanning || uiState.scannedWifiCount > 0 || uiState.scannedCellCount > 0 || uiState.scannedBluetoothCount > 0,
            enter = fadeIn(AppMotion.defaultEffectsSpec()),
            exit = fadeOut(AppMotion.fastEffectsSpec()),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = AppSpacing.lg)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(
                            R.string.scanned_wifi_count,
                            uiState.scannedWifiCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.scanned_cell_count,
                            uiState.scannedCellCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.scanned_bt_count,
                            uiState.scannedBluetoothCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = AppSpacing.lg)
        ) {
            MapControlButton(
                icon = Icons.Rounded.Layers,
                onClick = { showMapTypeDialog = true },
                contentDescription = stringResource(R.string.map_layers)
            )
        }

        if (uiState.isDrawingCollectionRoute) {
            Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.collection_route_center),
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        CollectionRouteControls(viewModel, uiState, mapController,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = AppSpacing.lg))
    }

    if (showMapTypeDialog) {
        MapTypeDialog(
            currentMapType = uiState.mapType,
            onMapTypeSelected = { viewModel.setMapType(it) },
            currentMapEngine = uiState.mapEngine,
            onMapEngineSelected = { viewModel.setMapEngine(it) },
            onDismiss = { showMapTypeDialog = false }
        )
    }
}
