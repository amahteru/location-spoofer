package com.vincenthzr.locationspoofer.viewmodel

import com.vincenthzr.locationspoofer.data.motion.MotionController
import java.util.Locale
import androidx.lifecycle.viewModelScope
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.model.RoutePlanStage
import com.vincenthzr.locationspoofer.data.model.RouteRunMode
import com.vincenthzr.locationspoofer.data.model.SavedLocation
import com.vincenthzr.locationspoofer.data.model.SimMode
import com.vincenthzr.locationspoofer.data.state.SpoofingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
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
}

internal fun MainViewModel.deleteSavedRoute(route: com.vincenthzr.locationspoofer.data.model.SavedRoute) {
    viewModelScope.launch(Dispatchers.IO) {
        val routes = locationRepository.getSavedRoutes().first()
        val entity = routes.find { it.name == route.name }
        if (entity != null) {
            locationRepository.deleteSavedRoute(entity)
        }
    }
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
    _uiState.update {
        it.copy(
            latitudeInput = String.format(java.util.Locale.US, "%.6f", lat),
            longitudeInput = String.format(java.util.Locale.US, "%.6f", lng),
            mapConfirmedPoint = Pair(lat, lng),
            showCoordinateError = false
        )
    }
    evaluateMockCapabilities()
    val state = _uiState.value
    restoredSpoofPoint?.let { restored ->
        if (com.vincenthzr.locationspoofer.utils.GeoMath.distance(restored, com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng(lat, lng)) < 1.0) return
        restoredSpoofPoint = null
    }
    if (state.isSpoofingActive) {
        settingsRepository.lastSpoofedLat = lat.toString()
        settingsRepository.lastSpoofedLng = lng.toString()
        viewModelScope.launch {
            if (state.mockWifi && !hasLocalWifiWithin50m(lat, lng) && !isDragging) {
                fetchWifiFromWigleSync(lat, lng)
            }
            if (state.mockCell && !hasLocalCellsWithin50m(lat, lng) && !isDragging) {
                fetchCellFromOpenCellIdSync(lat, lng)
            }
            evaluateMockCapabilitiesSuspend(lat, lng)
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
                mockWifi = updatedState.mockWifi && updatedState.canMockWifi,
                mockCell = updatedState.mockCell,
                mockBluetooth = updatedState.mockBluetooth && updatedState.canMockBluetooth,
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
        android.widget.Toast.makeText(
            context,
            context.getString(com.vincenthzr.locationspoofer.ui.R.string.disable_continuous_scan_route_first),
            android.widget.Toast.LENGTH_SHORT
        ).show()
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
                    val stepEndIndices = mutableListOf<Int>()
                    for (step in path.steps) {
                        for (polyline in step.polyline) {
                            segmentPoints.add(
                                RoutePoint(
                                    polyline.latitude,
                                    polyline.longitude,
                                    0.0
                                )
                            )
                        }
                        if (segmentPoints.isNotEmpty()) {
                            stepEndIndices.add(segmentPoints.size - 1)
                        }
                    }
                    val trafficLights = path.totalTrafficlights
                    if (trafficLights > 0 && stepEndIndices.isNotEmpty()) {
                        stepEndIndices.shuffled().take(trafficLights).forEach { idx ->
                            segmentPoints[idx] = segmentPoints[idx].copy(waitSec = 15.0)
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
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.route_plan_failed_fallback_straight),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
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
                        msg = context.getString(R.string.amap_quota_exhausted_fallback_format, errMsg)
                    }
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG)
                    .show()
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
        val records = environmentRecordsAt(startPoint.lat, startPoint.lng)
        val (wifi, cell, bluetooth) = locationToJson(records, startPoint.lat, startPoint.lng)
        _uiState.update { it.copy(collectedWifiJson = wifi, collectedCellJson = cell, collectedBluetoothJson = bluetooth) }
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
            _uiState.value.mockWifi,
            _uiState.value.mockCell,
            _uiState.value.mockBluetooth,
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
    settingsRepository.isSpoofingActive = false
    locationSyncJob?.cancel()
    locationSyncJob = null
    motionController.onStopped()
    environmentUpdateJob?.cancel()
    viewModelScope.launch {
        locationRepository.stopSpoofing(context)
        _uiState.update {
            it.copy(
                isSpoofingActive = false,
                routePlanStage = RoutePlanStage.IDLE,
                routePoints = emptyList(),
                routeRunMode = RouteRunMode.LOOP
            )
        }
    }
}

internal fun MainViewModel.saveCurrentLocation(name: String) {
    val lng = _uiState.value.longitudeInput.toDoubleOrNull() ?: return
    val lat = _uiState.value.latitudeInput.toDoubleOrNull() ?: return
    val state = _uiState.value
    settingsRepository.addSavedLocation(
        SavedLocation(
            name,
            lat,
            lng,
            state.collectedWifiJson,
            state.collectedCellJson,
            state.collectedBluetoothJson
        )
    )
    _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
}

private fun MainViewModel.resolveFavoriteName(record: com.vincenthzr.locationspoofer.data.db.CompleteLocation): String {
    val lat = record.location.lat
    val lng = record.location.lng
    return when {
        record.location.placeName.isNotBlank() -> record.location.placeName
        record.location.remark.isNotBlank() -> record.location.remark
        else -> String.format(Locale.US, "(%.5f, %.5f)", lat, lng)
    }
}

internal fun MainViewModel.toggleCollectedLocationFavorite(
    locationId: Long,
    onResult: (FavoriteToggleResult) -> Unit
) {
    viewModelScope.launch {
        favoriteToggleMutex.withLock {
            val record = withContext(Dispatchers.IO) {
                environmentDao.getCompleteLocationById(locationId)
            }
            if (record == null) {
                onResult(FavoriteToggleResult.Failed)
                return@withLock
            }

            val lat = record.location.lat
            val lng = record.location.lng
            val existing = settingsRepository.getSavedLocations().firstOrNull {
                it.sourceLocationId == locationId ||
                    (it.sourceLocationId == null && it.lat == lat && it.lng == lng)
            }

            if (existing != null) {
                settingsRepository.removeSavedLocation(existing)
                _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
                onResult(FavoriteToggleResult.Removed(existing.name))
            } else {
                val name = resolveFavoriteName(record)
                val (wifiJson, cellJson, btJson) = locationToJson(listOf(record), lat, lng)
                settingsRepository.addSavedLocation(
                    SavedLocation(name, lat, lng, wifiJson, cellJson, btJson, sourceLocationId = locationId)
                )
                _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
                onResult(FavoriteToggleResult.Added(name))
            }
        }
    }
}

internal fun MainViewModel.syncFavoriteCoordinateIfExists(locationId: Long, newLat: Double, newLng: Double) {
    val existing = settingsRepository.getSavedLocations()
        .firstOrNull { it.sourceLocationId == locationId } ?: return
    if (existing.lat == newLat && existing.lng == newLng) return
    settingsRepository.removeSavedLocation(existing)
    settingsRepository.addSavedLocation(existing.copy(lat = newLat, lng = newLng))
    _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
}

internal fun MainViewModel.parseWifiCount(wifiJson: String?): Int {
    if (wifiJson.isNullOrBlank()) return 0
    return try {
        val obj = org.json.JSONObject(wifiJson)
        val nearbyCount = obj.optJSONArray("nearbyWifi")?.length() ?: 0
        val connectedCount = if (obj.optBoolean("isConnected", false) && !obj.isNull("connectedWifi")) 1 else 0
        nearbyCount + connectedCount
    } catch (e: Exception) {
        try {
            org.json.JSONArray(wifiJson).length()
        } catch (e2: Exception) {
            0
        }
    }
}

internal fun MainViewModel.loadSavedLocation(loc: SavedLocation) {
    val wifiCount = parseWifiCount(loc.wifiJson)
    _uiState.update {
        it.copy(
            latitudeInput = String.format(java.util.Locale.US, "%.6f", loc.lat),
            longitudeInput = String.format(java.util.Locale.US, "%.6f", loc.lng),
            collectedWifiJson = loc.wifiJson,
            collectedCellJson = loc.cellJson,
            wifiApCount = wifiCount,
            wifiLoadStatus = if (wifiCount > 0) com.vincenthzr.locationspoofer.data.model.WifiLoadStatus.DONE else com.vincenthzr.locationspoofer.data.model.WifiLoadStatus.IDLE
        )
    }
}

internal fun MainViewModel.removeSavedLocation(location: SavedLocation) {
    settingsRepository.removeSavedLocation(location)
    _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
}

internal fun MainViewModel.addSavedRoute(name: String) {
    val points = _uiState.value.routePoints
    if (points.size >= 2) {
        settingsRepository.addSavedRoute(
            com.vincenthzr.locationspoofer.data.model.SavedRoute(
                name,
                points
            )
        )
        _uiState.update { it.copy(savedRoutes = settingsRepository.getSavedRoutes()) }
    }
}

internal fun MainViewModel.removeSavedRoute(route: com.vincenthzr.locationspoofer.data.model.SavedRoute) {
    settingsRepository.removeSavedRoute(route)
    _uiState.update { it.copy(savedRoutes = settingsRepository.getSavedRoutes()) }
}

internal fun MainViewModel.loadSavedRoute(route: com.vincenthzr.locationspoofer.data.model.SavedRoute) {
    _uiState.update {
        it.copy(
            routePoints = route.points,
            routePlanStage = com.vincenthzr.locationspoofer.data.model.RoutePlanStage.READY
        )
    }
}

internal fun MainViewModel.observeMotionController() {
    viewModelScope.launch {
        motionController.state.collect { st ->
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

    val dLat = Math.toRadians(lat - lastDbQueryLat)
    val dLng = Math.toRadians(lng - lastDbQueryLng)
    val a = kotlin.math.sin(dLat / 2).let { it * it } + kotlin.math.cos(
        Math.toRadians(lastDbQueryLat)
    ) * kotlin.math.cos(Math.toRadians(lat)) * kotlin.math.sin(dLng / 2).let { it * it }
    val distance =
        2 * 6378137.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

    val coveredRouteId = com.vincenthzr.locationspoofer.data.model.CollectionRouteMatcher.routeAt(
        collectionRoutes.value,
        com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng(lat, lng),
        _uiState.value.selectedCollectionRouteId
    )?.route?.id
    if (distance > 20.0 || coveredRouteId != lastEnvironmentRouteId) {
        lastEnvironmentRouteId = coveredRouteId
        lastDbQueryLat = lat
        lastDbQueryLng = lng

        environmentUpdateJob?.cancel()
        environmentUpdateJob = viewModelScope.launch {
            val records = environmentRecordsAt(lat, lng)
            val jsons = locationToJson(records, lat, lng)
            locationRepository.updateEnvironment(jsons.first, jsons.second, jsons.third)
        }
    }
}

