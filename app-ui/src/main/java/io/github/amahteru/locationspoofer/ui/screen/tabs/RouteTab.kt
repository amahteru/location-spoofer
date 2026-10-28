package io.github.amahteru.locationspoofer.ui.screen.tabs

import androidx.core.graphics.toColorInt
import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.*
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.AppMapMarker
import io.github.amahteru.locationspoofer.ui.components.MapTypeDialog
import io.github.amahteru.locationspoofer.ui.components.MarkerType
import io.github.amahteru.locationspoofer.ui.screen.PlaceSearchBar
import io.github.amahteru.locationspoofer.ui.screen.tabs.route.*
import io.github.amahteru.locationspoofer.ui.components.MapCoverageHelper
import io.github.amahteru.locationspoofer.viewmodel.prepareRouteSimulationOptions
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.setAltitude
import io.github.amahteru.locationspoofer.viewmodel.setAltitudeVariation
import io.github.amahteru.locationspoofer.viewmodel.ManageDataViewModel
import io.github.amahteru.locationspoofer.viewmodel.addRoutePoint
import io.github.amahteru.locationspoofer.viewmodel.fetchCurrentLocation
import io.github.amahteru.locationspoofer.viewmodel.finishSelectingPoints
import io.github.amahteru.locationspoofer.viewmodel.restartSelectingPoints
import io.github.amahteru.locationspoofer.viewmodel.setCustomSpeedMs
import io.github.amahteru.locationspoofer.viewmodel.setMapEngine
import io.github.amahteru.locationspoofer.viewmodel.setMapType
import io.github.amahteru.locationspoofer.viewmodel.setRouteRunMode
import io.github.amahteru.locationspoofer.viewmodel.setRouteSimMode
import io.github.amahteru.locationspoofer.viewmodel.setSatelliteCount
import io.github.amahteru.locationspoofer.viewmodel.setStopAtDestination
import io.github.amahteru.locationspoofer.viewmodel.setUseRealRoute
import io.github.amahteru.locationspoofer.viewmodel.startRoutePlanning
import io.github.amahteru.locationspoofer.viewmodel.stopRoutePlanning
import io.github.amahteru.locationspoofer.viewmodel.toggleEnableJitter
import io.github.amahteru.locationspoofer.viewmodel.toggleMockBluetooth
import io.github.amahteru.locationspoofer.viewmodel.toggleMockCell
import io.github.amahteru.locationspoofer.viewmodel.toggleMockWifi
import io.github.amahteru.locationspoofer.viewmodel.undoLastRoutePoint
import org.koin.androidx.compose.koinViewModel
import io.github.amahteru.locationspoofer.ui.theme.warning
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun RouteTab(
    viewModel: MainViewModel,
    uiState: AppState,
    mapController: AppMapController?,
    isActive: Boolean = true,
    bottomBarHeight: Dp = 90.dp,
    manageDataViewModel: ManageDataViewModel = koinViewModel()
) {
    val motion = AppMotion
    val collectionRoutes by viewModel.collectionRoutes.collectAsState()
    val context = LocalContext.current
    var showMapTypeDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var showSavedRoutesDialog by remember { mutableStateOf(false) }
    var bottomActionHeightPx by remember { mutableIntStateOf(0) }

    val stage = uiState.routePlanStage
    val isRunning = stage == RoutePlanStage.RUNNING
    val isManual = uiState.routeRunMode == RouteRunMode.MANUAL
    val routePoints = uiState.routePoints

    val density = LocalDensity.current
    val bottomActionHeightDp = with(density) { bottomActionHeightPx.toDp() }

    LaunchedEffect(mapController, uiState.mapType, isActive) {
        if (!isActive) return@LaunchedEffect
        mapController?.setMapType(uiState.mapType)
    }

    val manageDataList = manageDataViewModel.uiState.collectAsState().value.dataList
    var liveMarker by remember { mutableStateOf<AppMapMarker?>(null) }
    LaunchedEffect(routePoints, mapController, manageDataList, isActive, collectionRoutes, uiState.selectedCollectionRouteId) {
        if (!isActive) return@LaunchedEffect
        val map = mapController ?: return@LaunchedEffect
        map.clear()
        collectionRoutes.firstOrNull { it.route.id == uiState.selectedCollectionRouteId }?.let {
            io.github.amahteru.locationspoofer.ui.components.CollectionRouteMapHelper.draw(map, it.route)
        }
        liveMarker = null
        val locations = manageDataList.map { it.location }
        MapCoverageHelper.drawCoverage(map, locations)

        if (routePoints.size >= 2) {
            map.addPolyline(
                routePoints.map { Pair(it.lat, it.lng) },
                "#FF388BFD".toColorInt(),
                8f
            )
        }
        routePoints.forEachIndexed { idx, p ->
            val type = when {
                idx == 0 -> MarkerType.GREEN
                idx == routePoints.lastIndex && routePoints.size > 1 -> MarkerType.RED
                else -> MarkerType.DEFAULT
            }
            if (uiState.useRealRoute && uiState.routePlanStage == RoutePlanStage.RUNNING && type == MarkerType.DEFAULT) {
                return@forEachIndexed
            }
            val startBadge = context.getString(R.string.route_start_badge)
            val endBadge = context.getString(R.string.route_end_badge)
            val label = when (type) {
                MarkerType.GREEN -> startBadge
                MarkerType.RED -> endBadge
                else -> "${idx + 1}"
            }
            map.addMarker(
                p.lat,
                p.lng,
                if (type == MarkerType.RED && uiState.useRealRoute && uiState.routePlanStage == RoutePlanStage.RUNNING) endBadge else label,
                type
            )
        }

        if (uiState.isSpoofingActive) {
            val currentLat = uiState.latitudeInput.toDoubleOrNull()
            val currentLng = uiState.longitudeInput.toDoubleOrNull()
            if (currentLat != null && currentLng != null) {
                liveMarker = map.addMarker(
                    currentLat, currentLng,
                    context.getString(R.string.current_location),
                    MarkerType.ORANGE
                )
            }
        }
    }

    val lat = uiState.latitudeInput.toDoubleOrNull()
    val lng = uiState.longitudeInput.toDoubleOrNull()
    LaunchedEffect(lat, lng, uiState.isSpoofingActive, uiState.routePlanStage) {
        if (uiState.isSpoofingActive && lat != null && lng != null) {
            if (uiState.routePlanStage != RoutePlanStage.RUNNING) {
                mapController?.animateCamera(lat, lng)
            }
            if (liveMarker != null) {
                liveMarker?.setPosition(lat, lng)
            } else {
                liveMarker = mapController?.addMarker(
                    lat, lng,
                    context.getString(R.string.current_location),
                    MarkerType.ORANGE
                )
            }
        }
    }

    LaunchedEffect(uiState.routePlanStage, routePoints, isActive, mapController, uiState.selectedCollectionRouteId) {
        if (!isActive) return@LaunchedEffect
        if ((uiState.routePlanStage == RoutePlanStage.RUNNING ||
                (uiState.routePlanStage == RoutePlanStage.READY && uiState.selectedCollectionRouteId != null)) && routePoints.size >= 2) {
            val padLeft = with(density) { 36.dp.roundToPx() }
            val padTop = with(density) { 80.dp.roundToPx() }
            val padRight = with(density) { 36.dp.roundToPx() }
            val effectiveBottomDp =
                if (bottomActionHeightDp > 0.dp) bottomActionHeightDp + bottomBarHeight + 24.dp else bottomBarHeight + 160.dp
            val padBottom =
                with(density) { effectiveBottomDp.roundToPx() }
            mapController?.fitBounds(
                points = routePoints.map { Pair(it.lat, it.lng) },
                paddingLeft = padLeft,
                paddingTop = padTop,
                paddingRight = padRight,
                paddingBottom = padBottom
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if ((stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE)) {
            Icon(
                Icons.Rounded.AddLocationAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .padding(bottom = AppSpacing.lg)
            )
        }

        if (isRunning && isManual) {
            Icon(
                Icons.Rounded.PersonPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.warning,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .padding(bottom = AppSpacing.lg)
            )
        }

        if (routePoints.isNotEmpty() && (stage == RoutePlanStage.IDLE || stage == RoutePlanStage.SELECTING)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = AppSpacing.md)
                    .animateContentSize(motion.defaultSpatialSpec()),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.xs, top = AppSpacing.xs, bottom = AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Text(
                        stringResource(R.string.selected_waypoints_count, routePoints.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    VerticalDivider(modifier = Modifier.height(24.dp))

                    IconButton(onClick = { viewModel.undoLastRoutePoint() }) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.undo))
                    }

                    IconButton(onClick = { viewModel.restartSelectingPoints() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.reselect))
                    }
                }
            }
        }

        val animatedBottomPadding by animateDpAsState(
            targetValue = bottomBarHeight + AppSpacing.sm + bottomActionHeightDp + AppSpacing.lg,
            animationSpec = motion.defaultSpatialSpec(),
            label = "fab_padding"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AppSpacing.lg, bottom = animatedBottomPadding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RouteControlButton(
                    icon = Icons.Rounded.Layers,
                    onClick = { showMapTypeDialog = true }
                )
                RouteControlButton(
                    icon = Icons.Rounded.Bookmarks,
                    onClick = { showSavedRoutesDialog = true }
                )
                RouteControlButton(
                    icon = Icons.Rounded.MyLocation,
                    onClick = {
                        viewModel.fetchCurrentLocation(context) { lat, lng ->
                            mapController?.animateCamera(lat, lng, 16f)
                        }
                    }
                )
            }
        }

        RouteBottomPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarHeight + AppSpacing.sm, start = AppSpacing.lg, end = AppSpacing.lg)
                .onGloballyPositioned { coordinates ->
                    bottomActionHeightPx = coordinates.size.height
                },
            stage = stage,
            routePoints = routePoints,
            uiState = uiState,
            onConfirmPoint = {
                val tLat = mapController?.cameraTargetLat
                val tLng = mapController?.cameraTargetLng
                if (tLat != null && tLng != null) {
                    viewModel.addRoutePoint(tLat, tLng)
                } else {
                    val fallbackLat = uiState.latitudeInput.toDoubleOrNull()
                    val fallbackLng = uiState.longitudeInput.toDoubleOrNull()
                    if (fallbackLat != null && fallbackLng != null) {
                        viewModel.addRoutePoint(fallbackLat, fallbackLng)
                    }
                }
            },
            onFinishSelecting = {
                viewModel.finishSelectingPoints()
            },
            onRestartSelecting = {
                viewModel.restartSelectingPoints()
            },
            onSaveRoute = { showSaveRouteDialog = true },
            onStartPlanning = { viewModel.prepareRouteSimulationOptions { showConfigDialog = true } },
            onStopRoute = {
                viewModel.stopRoutePlanning()
            },
            searchBar = if (stage == RoutePlanStage.IDLE || stage == RoutePlanStage.SELECTING) {
                { barModifier ->
                    PlaceSearchBar(
                        mapEngine = uiState.mapEngine,
                        onResultClick = { poi -> mapController?.animateCamera(poi.lat, poi.lng, 17f) },
                        modifier = barModifier
                    )
                }
            } else null
        )
    }

    if (showConfigDialog) {
        RouteConfigDialog(
            uiState = uiState,
            onDismiss = {
                showConfigDialog = false
            },
            onStartRoute = {
                showConfigDialog = false
                viewModel.startRoutePlanning()
            },
            onRunModeChange = viewModel::setRouteRunMode,
            onSpeedChange = viewModel::setRouteSimMode,
            onCustomSpeedChange = viewModel::setCustomSpeedMs,
            onUseRealRouteChange = viewModel::setUseRealRoute,
            onStopAtDestinationChange = viewModel::setStopAtDestination,
            onToggleWifi = viewModel::toggleMockWifi,
            onToggleCell = viewModel::toggleMockCell,
            onToggleBluetooth = viewModel::toggleMockBluetooth,
            onToggleJitter = viewModel::toggleEnableJitter,
            onAltitudeChange = viewModel::setAltitude,
            onAltitudeVariationChange = viewModel::setAltitudeVariation,
            onSatelliteCountChange = viewModel::setSatelliteCount
        )
    }

    if (uiState.isFetchingRoute) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(AppSpacing.xxl)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.planning_route_trajectory),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
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

    if (showSaveRouteDialog) {
        SaveRouteDialog(
            routePoints = routePoints,
            viewModel = viewModel,
            onDismiss = { showSaveRouteDialog = false }
        )
    }

    if (showSavedRoutesDialog) {
        SavedRoutesDialog(
            uiState = uiState,
            viewModel = viewModel,
            mapController = mapController,
            bottomActionHeightDp = bottomActionHeightDp,
            bottomBarHeight = bottomBarHeight,
            onDismiss = { showSavedRoutesDialog = false }
        )
    }
}
