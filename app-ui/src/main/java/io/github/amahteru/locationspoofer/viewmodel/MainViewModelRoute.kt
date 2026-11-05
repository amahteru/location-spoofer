package io.github.amahteru.locationspoofer.viewmodel

import io.github.amahteru.locationspoofer.data.model.simulatedWifi
import io.github.amahteru.locationspoofer.data.model.simulatedCell
import io.github.amahteru.locationspoofer.data.model.simulatedBluetooth
import io.github.amahteru.locationspoofer.data.model.isAt
import io.github.amahteru.locationspoofer.data.db.displayName
import io.github.amahteru.locationspoofer.ui.components.AppSnackbar
import io.github.amahteru.locationspoofer.data.motion.MotionController
import java.util.Locale
import androidx.lifecycle.viewModelScope
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.data.model.RoutePoint
import io.github.amahteru.locationspoofer.data.model.RoutePlanStage
import io.github.amahteru.locationspoofer.data.model.RouteRunMode
import io.github.amahteru.locationspoofer.data.model.SavedLocation
import io.github.amahteru.locationspoofer.data.model.SimMode
import io.github.amahteru.locationspoofer.data.model.routePoints
import io.github.amahteru.locationspoofer.data.state.SpoofingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun MainViewModel.addRoutePoint(lat: Double, lng: Double) {
    _uiState.update { it.copy(routePoints = it.routePoints + RoutePoint(lat, lng)) }
}

internal fun MainViewModel.undoLastRoutePoint() {
    _uiState.update { state ->
        if (state.routePoints.isEmpty()) state
        else state.copy(routePoints = state.routePoints.dropLast(1))
    }
}

internal fun MainViewModel.finishSelectingPoints() {
    if (_uiState.value.routePoints.size < 2) return
    _uiState.update { it.copy(routePlanStage = RoutePlanStage.READY) }
}

internal fun MainViewModel.restartSelectingPoints() {
    _uiState.update {
        it.copy(
            routePoints = emptyList(),
            routePlanStage = RoutePlanStage.SELECTING,
            selectedCollectionRouteId = null
        )
    }
}

internal fun MainViewModel.setRouteRunMode(mode: RouteRunMode) {
    _uiState.update { it.copy(routeRunMode = mode) }
}

internal fun MainViewModel.saveRoute(name: String, points: List<RoutePoint>) {
    viewModelScope.launch(Dispatchers.IO) {
        locationRepository.insertSavedRoute(name, points)
    }
    AppSnackbar.show(context.getString(R.string.favorite_added, name))
}

internal fun MainViewModel.toggleCollectedRouteFavorite(route: io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord) {
    val points = route.routePoints()
    val existing = _uiState.value.savedRoutes.firstOrNull { it.points == points }
    if (existing != null) deleteSavedRoute(existing)
    else saveRoute(route.name.ifBlank { context.getString(R.string.collection_route_unnamed) }, points)
}

internal fun MainViewModel.deleteSavedRoute(route: io.github.amahteru.locationspoofer.data.model.SavedRoute) {
    viewModelScope.launch(Dispatchers.IO) {
        val entity = locationRepository.getSavedRoute(route.id) ?: return@launch
        locationRepository.deleteSavedRoute(route.id)
        AppSnackbar.show(
            context.getString(R.string.favorite_removed, route.name),
            actionLabel = context.getString(R.string.undo),
            onAction = { viewModelScope.launch(Dispatchers.IO) { locationRepository.insertSavedRouteEntity(entity) } }
        )
    }
}

internal fun MainViewModel.renameSavedRoute(route: io.github.amahteru.locationspoofer.data.model.SavedRoute, name: String) {
    viewModelScope.launch(Dispatchers.IO) {
        locationRepository.renameSavedRoute(route.id, name)
    }
    AppSnackbar.show(context.getString(R.string.save_success))
}

internal fun MainViewModel.setRouteSimMode(mode: SimMode) {
    _uiState.update { it.copy(routeSimMode = mode) }
}

internal fun MainViewModel.setCustomSpeedMs(speed: Double) {
    _uiState.update { it.copy(customSpeedMs = speed.coerceIn(0.1, 100.0)) }
}

private fun MainViewModel.getEffectiveSpeedMs(): Double {
    val state = _uiState.value
    return if (state.routeSimMode == SimMode.CUSTOM) state.customSpeedMs
    else state.routeSimMode.speedMs
}

internal fun MainViewModel.joystickMaxSpeedMs(): Float =
    getEffectiveSpeedMs().takeIf { it > 0.0 }?.toFloat() ?: SimMode.WALKING.speedMs.toFloat()

internal fun MainViewModel.confirmMapPoint(lat: Double, lng: Double, isDragging: Boolean = false) {
    if (motionController.state.value.hasRoute) return
    val before = _uiState.value
    val currentLat = (if (before.isSpoofingActive) settingsRepository.lastSpoofedLat else before.latitudeInput).toDoubleOrNull()
    val currentLng = (if (before.isSpoofingActive) settingsRepository.lastSpoofedLng else before.longitudeInput).toDoubleOrNull()
    val unchanged = currentLat != null && currentLng != null &&
        io.github.amahteru.locationspoofer.utils.GeoMath.distance(io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng(currentLat, currentLng), io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng(lat, lng)) < 1.0
    if (unchanged && (isDragging || !mapDragPending)) return
    mapDragPending = isDragging
    _uiState.update {
        it.copy(
            latitudeInput = String.format(java.util.Locale.US, "%.6f", lat),
            longitudeInput = String.format(java.util.Locale.US, "%.6f", lng),
            showCoordinateError = false
        )
    }
    evaluateMockCapabilities()
    val state = _uiState.value
    if (state.isSpoofingActive) {
        settingsRepository.lastSpoofedLat = lat.toString()
        settingsRepository.lastSpoofedLng = lng.toString()
        viewModelScope.launch {
            val fetched = !isDragging && fetchMissingEnvironment(lat, lng, state.mockWifi, state.mockCell)
            evaluateMockCapabilitiesSuspend(state.latitudeInput.toDouble(), state.longitudeInput.toDouble(), fetched)
            val updatedState = _uiState.value
            locationRepository.updateConfig(
                lat = lat,
                lng = lng,
                simMode = "STILL",
                simBearing = 0f,
                startTime = SpoofingState.startTimestamp,
                routePoints = emptyList(),
                isRouteMode = false,
                wifiJson = updatedState.collectedWifiJson,
                cellJson = updatedState.collectedCellJson,
                bluetoothJson = updatedState.collectedBluetoothJson,
                mockWifi = updatedState.simulatedWifi,
                mockCell = updatedState.simulatedCell,
                mockBluetooth = updatedState.simulatedBluetooth,
                enableJitter = updatedState.enableJitter
            )
            motionController.onStaticStarted(lat, lng)
        }
    }
}

internal fun MainViewModel.setUseRealRoute(use: Boolean) {
    _uiState.update { it.copy(useRealRoute = use) }
}

internal fun MainViewModel.setStopAtDestination(stop: Boolean) {
    _uiState.update { it.copy(stopAtDestination = stop) }
}

internal fun MainViewModel.startRoutePlanning() {
    val state = _uiState.value
    if (state.isContinuousScanning || state.isStoppingCollection) {
        AppSnackbar.show(context.getString(io.github.amahteru.locationspoofer.ui.R.string.disable_continuous_scan_route_first))
        return
    }
    if (state.routePoints.size < 2) return
    if (state.routeRunMode == RouteRunMode.MANUAL && !ensureOverlayPermission()) return

    if (state.useRealRoute) {
        _uiState.update { it.copy(isFetchingRoute = true) }
        fetchRealRouteAndStart(state.routePoints, state)
    } else {
        startSimulationWithPoints(state.routePoints, state)
    }
}

@Suppress("DEPRECATION")
private fun MainViewModel.fetchRealRouteAndStart(points: List<RoutePoint>, state: AppState) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            com.amap.api.services.core.ServiceSettings.updatePrivacyShow(context, true, true)
            com.amap.api.services.core.ServiceSettings.updatePrivacyAgree(context, true)

            val routeSearch = com.amap.api.services.route.RouteSearch(context)
            val allRealPoints = mutableListOf<RoutePoint>()
            var hasError = false

            for (i in 0 until points.size - 1) {
                val start = com.amap.api.services.core.LatLonPoint(points[i].lat, points[i].lng)
                val end =
                    com.amap.api.services.core.LatLonPoint(points[i + 1].lat, points[i + 1].lng)
                val fromAndTo = com.amap.api.services.route.RouteSearch.FromAndTo(start, end)

                val query = com.amap.api.services.route.RouteSearch.DriveRouteQuery(
                    fromAndTo,
                    com.amap.api.services.route.RouteSearch.DrivingDefault,
                    null,
                    null,
                    ""
                )

                val result = routeSearch.calculateDriveRoute(query)
                if (result != null && result.paths.isNotEmpty()) {
                    val path = result.paths[0]
                    val segmentPoints = mutableListOf<RoutePoint>()
                    for (step in path.steps) {
                        for (polyline in step.polyline) {
                            segmentPoints.add(RoutePoint(polyline.latitude, polyline.longitude))
                        }
                    }
                    allRealPoints.addAll(segmentPoints)
                } else {
                    hasError = true
                    break
                }
            }

            if (!hasError && allRealPoints.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isFetchingRoute = false) }
                    startSimulationWithPoints(allRealPoints, state)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isFetchingRoute = false) }
                    AppSnackbar.show(context.getString(R.string.route_plan_failed_fallback_straight), long = true)
                    startSimulationWithPoints(points, state)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isFetchingRoute = false) }
                var msg = context.getString(R.string.route_request_exception, e.message ?: "")
                if (e is com.amap.api.services.core.AMapException) {
                    val errCode = e.errorCode
                    val errMsg = e.errorMessage ?: ""
                    msg = context.getString(R.string.amap_api_exception_format, errCode, errMsg)
                    if (errCode == 10003 || errCode == 10012 || errCode == 10013 || errCode == 1800 || errCode == 18000 ||
                        errMsg.contains("额度") || errMsg.contains("limit", ignoreCase = true)
                    ) {
                        msg = context.getString(R.string.amap_quota_exhausted_fallback_format)
                    }
                }
                AppSnackbar.show(msg, long = true)
                startSimulationWithPoints(points, state)
            }
        }
    }
}

private fun MainViewModel.startSimulationWithPoints(pointsToRun: List<RoutePoint>, state: AppState) {
    val startPoint = pointsToRun.first()

    _uiState.update {
        it.copy(
            latitudeInput = String.format(java.util.Locale.US, "%.6f", startPoint.lat),
            longitudeInput = String.format(java.util.Locale.US, "%.6f", startPoint.lng),
            routePlanStage = RoutePlanStage.RUNNING,
            routePoints = pointsToRun
        )
    }

    val isLoop = _uiState.value.routeRunMode == RouteRunMode.LOOP
    val now = System.currentTimeMillis()
    val speed = getEffectiveSpeedMs()

    SpoofingState.startTimestamp = now
    SpoofingState.latitude = startPoint.lat
    SpoofingState.longitude = startPoint.lng
    SpoofingState.simBearing = 0f

    viewModelScope.launch {
        applyEnvironment(environmentRecordsAt(startPoint.lat, startPoint.lng), startPoint.lat, startPoint.lng)
        locationRepository.startSpoofing(
            context,
            startPoint.lat,
            startPoint.lng,
            if (isLoop) _uiState.value.routeSimMode.name else "STILL",
            0f,
            now,
            pointsToRun,
            isLoop,
            _uiState.value.collectedWifiJson,
            _uiState.value.collectedCellJson,
            _uiState.value.collectedBluetoothJson,
            _uiState.value.simulatedWifi,
            _uiState.value.simulatedCell,
            _uiState.value.simulatedBluetooth,
            _uiState.value.enableJitter,
            speedMs = speed,
            stopAtDestination = _uiState.value.stopAtDestination
        )
        val preset = MotionController.SpeedPreset(_uiState.value.routeSimMode.name, joystickMaxSpeedMs().toDouble())
        motionController.onRouteStarted(pointsToRun, preset, _uiState.value.stopAtDestination, now, startPaused = !isLoop)
        if (!isLoop) locationRepository.setFloatingJoystickVisible(context, true)
        _uiState.update {
            it.copy(isSpoofingActive = true)
        }
    }
}

internal fun MainViewModel.stopRoutePlanning() {
    stopSpoofing()
    resetRouteState()
}

private fun MainViewModel.resetRouteState() {
    environmentUpdateJob?.cancel()
    _uiState.update {
        it.copy(
            routePlanStage = RoutePlanStage.IDLE,
            routePoints = emptyList(),
            routeRunMode = RouteRunMode.LOOP
        )
    }
}

internal fun MainViewModel.pauseRoute() {
    motionController.pause()
}

internal fun MainViewModel.resumeRoute() {
    motionController.resume()
}

internal fun MainViewModel.holdRouteAtCurrentPosition() {
    switchRouteToStatic(RoutePlanStage.IDLE, keepStartPoint = false)
}

internal fun MainViewModel.replanRouteFromCurrentPosition() {
    switchRouteToStatic(RoutePlanStage.SELECTING, keepStartPoint = true)
}

private fun MainViewModel.switchRouteToStatic(nextStage: RoutePlanStage, keepStartPoint: Boolean) {
    val lat = _uiState.value.latitudeInput.toDoubleOrNull() ?: return
    val lng = _uiState.value.longitudeInput.toDoubleOrNull() ?: return
    motionController.onStaticStarted(lat, lng)
    settingsRepository.lastSpoofedLat = lat.toString()
    settingsRepository.lastSpoofedLng = lng.toString()
    _uiState.update {
        it.copy(
            routePlanStage = nextStage,
            routePoints = if (keepStartPoint) listOf(RoutePoint(lat, lng)) else emptyList(),
            routeRunMode = RouteRunMode.LOOP,
            selectedCollectionRouteId = null
        )
    }
    viewModelScope.launch {
        val state = _uiState.value
        locationRepository.updateConfig(
            lat = lat,
            lng = lng,
            simMode = "STILL",
            simBearing = 0f,
            startTime = SpoofingState.startTimestamp,
            routePoints = emptyList(),
            isRouteMode = false,
            mockWifi = state.simulatedWifi,
            mockCell = state.simulatedCell,
            mockBluetooth = state.simulatedBluetooth,
            enableJitter = state.enableJitter
        )
    }
}

internal fun MainViewModel.saveCurrentLocation(name: String) {
    val lng = _uiState.value.longitudeInput.toDoubleOrNull() ?: return
    val lat = _uiState.value.latitudeInput.toDoubleOrNull() ?: return
    settingsRepository.addSavedLocation(SavedLocation(name, lat, lng))
    _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
    AppSnackbar.show(context.getString(R.string.favorite_added, name))
}

internal fun MainViewModel.toggleCollectedLocationFavorite(locationId: Long) {
    viewModelScope.launch {
        favoriteToggleMutex.withLock {
            val record = withContext(Dispatchers.IO) {
                environmentDao.getCompleteLocationById(locationId)
            }
            if (record == null) {
                AppSnackbar.show(context.getString(R.string.favorite_failed), long = true)
                return@withLock
            }

            val lat = record.location.lat
            val lng = record.location.lng
            val existing = settingsRepository.getSavedLocations().firstOrNull { it.isAt(lat, lng) }
            if (existing != null) {
                removeSavedLocation(existing)
            } else {
                val name = record.location.displayName()
                settingsRepository.addSavedLocation(SavedLocation(name, lat, lng))
                _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
                AppSnackbar.show(context.getString(R.string.favorite_added, name))
            }
        }
    }
}

internal fun MainViewModel.syncFavoriteCoordinateIfExists(oldLat: Double, oldLng: Double, newLat: Double, newLng: Double) {
    settingsRepository.setSavedLocations(
        settingsRepository.getSavedLocations()
            .map { if (it.isAt(oldLat, oldLng)) it.copy(lat = newLat, lng = newLng) else it }
            .distinctBy { it.lat to it.lng }
    )
    _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
}

internal fun MainViewModel.loadSavedLocation(loc: SavedLocation) {
    _uiState.update {
        it.copy(
            latitudeInput = String.format(java.util.Locale.US, "%.6f", loc.lat),
            longitudeInput = String.format(java.util.Locale.US, "%.6f", loc.lng),
        )
    }
    evaluateMockCapabilities()
}

internal fun MainViewModel.removeSavedLocation(location: SavedLocation) {
    val index = settingsRepository.getSavedLocations().indexOfFirst { it.isAt(location.lat, location.lng) }
    settingsRepository.removeSavedLocation(location)
    _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
    AppSnackbar.show(
        context.getString(R.string.favorite_removed, location.name),
        actionLabel = context.getString(R.string.undo),
        onAction = {
            val saved = settingsRepository.getSavedLocations().toMutableList()
            if (saved.none { it.isAt(location.lat, location.lng) }) {
                saved.add(index.coerceIn(0, saved.size), location)
                settingsRepository.setSavedLocations(saved)
                _uiState.update { it.copy(savedLocations = saved) }
            }
        }
    )
}

internal fun MainViewModel.renameSavedLocation(location: SavedLocation, name: String) {
    settingsRepository.setSavedLocations(
        settingsRepository.getSavedLocations().map { if (it == location) it.copy(name = name) else it }
    )
    _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
    AppSnackbar.show(context.getString(R.string.save_success))
}

internal fun MainViewModel.loadSavedRoute(route: io.github.amahteru.locationspoofer.data.model.SavedRoute) {
    _uiState.update {
        it.copy(
            routePoints = route.points,
            routePlanStage = io.github.amahteru.locationspoofer.data.model.RoutePlanStage.READY
        )
    }
}

internal fun MainViewModel.observeMotionController() {
    viewModelScope.launch {
        var hadRoute = false
        motionController.state.collect { st ->
            val routeEnded = hadRoute && !st.hasRoute
            hadRoute = st.hasRoute
            if (routeEnded && _uiState.value.routePlanStage == RoutePlanStage.RUNNING) {
                resetRouteState()
            }
            if (st.paused != _uiState.value.isRoutePaused) {
                _uiState.update { it.copy(isRoutePaused = st.paused) }
            }
            if (!st.active) return@collect
            if (st.mode == MotionController.Mode.ROUTE || st.mode == MotionController.Mode.MANUAL) {
                updatePosition(st.lat, st.lng, st.bearing)
            }
            val simMode = SimMode.entries.firstOrNull { it.name == st.preset.name }
            if (simMode != null && simMode != _uiState.value.routeSimMode) {
                _uiState.update { it.copy(routeSimMode = simMode) }
            }
        }
    }
}

private fun MainViewModel.updatePosition(lat: Double, lng: Double, bearing: Float) {
    _uiState.update {
        it.copy(
            latitudeInput = String.format(java.util.Locale.US, "%.6f", lat),
            longitudeInput = String.format(java.util.Locale.US, "%.6f", lng),
            simBearing = bearing,
            showCoordinateError = false
        )
    }
    SpoofingState.latitude = lat
    SpoofingState.longitude = lng
    SpoofingState.simBearing = bearing

    val distance = io.github.amahteru.locationspoofer.utils.GeoMath.distance(
        io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng(lastDbQueryLat, lastDbQueryLng),
        io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng(lat, lng)
    )

    val coveredRouteId = io.github.amahteru.locationspoofer.data.model.CollectionRouteMatcher.routeAt(
        collectionRoutes.value,
        io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng(lat, lng),
        _uiState.value.selectedCollectionRouteId
    )?.route?.id
    if (distance > 20.0 || coveredRouteId != lastEnvironmentRouteId) {
        lastEnvironmentRouteId = coveredRouteId
        lastDbQueryLat = lat
        lastDbQueryLng = lng

        environmentUpdateJob?.cancel()
        environmentUpdateJob = viewModelScope.launch {
            applyEnvironment(environmentRecordsAt(lat, lng), lat, lng)
            val state = _uiState.value
            locationRepository.updateEnvironment(
                state.collectedWifiJson,
                state.collectedCellJson,
                state.collectedBluetoothJson,
                state.simulatedWifi,
                state.simulatedCell,
                state.simulatedBluetooth
            )
        }
    }
}

