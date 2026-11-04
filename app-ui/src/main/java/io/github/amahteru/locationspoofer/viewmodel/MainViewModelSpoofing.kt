package io.github.amahteru.locationspoofer.viewmodel

import io.github.amahteru.locationspoofer.data.model.simulatedWifi
import io.github.amahteru.locationspoofer.data.model.simulatedBluetooth
import io.github.amahteru.locationspoofer.data.model.CollectionIssue
import io.github.amahteru.locationspoofer.data.model.EnvironmentResolver
import io.github.amahteru.locationspoofer.ui.components.AppSnackbar
import androidx.core.net.toUri
import io.github.amahteru.locationspoofer.ui.BuildConfig
import androidx.lifecycle.viewModelScope
import io.github.amahteru.locationspoofer.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MainViewModel.startSpoofing() {
    val state = _uiState.value

    if (state.isContinuousScanning || state.isStoppingCollection) {
        AppSnackbar.show(context.getString(io.github.amahteru.locationspoofer.ui.R.string.disable_continuous_scan_first))
        return
    }

    val lng = state.longitudeInput.toDoubleOrNull()
    val lat = state.latitudeInput.toDoubleOrNull()
    if (lng == null || lat == null || lng !in -180.0..180.0 || lat !in -90.0..90.0) {
        _uiState.update { it.copy(showCoordinateError = true) }
        return
    }

    settingsRepository.isSpoofingActive = true
    settingsRepository.lastSpoofedLat = lat.toString()
    settingsRepository.lastSpoofedLng = lng.toString()

    viewModelScope.launch {
        _uiState.update { it.copy(isSavingConfig = true) }

        if (state.mockWifi && !EnvironmentResolver.hasWifi(environmentRecordsAt(lat, lng))) {
            fetchWifiFromWigleSync(lat, lng)
        }
        if (state.mockCell && !EnvironmentResolver.hasCell(environmentRecordsAt(lat, lng))) {
            fetchCellFromOpenCellIdSync(lat, lng)
        }

        evaluateMockCapabilitiesSuspend(lat, lng)

        val updatedState = _uiState.value
        val now = System.currentTimeMillis()
        locationRepository.startSpoofing(
            context, lat, lng,
            "STILL", 0f, now,
            emptyList(), false,
            updatedState.collectedWifiJson,
            updatedState.collectedCellJson,
            updatedState.collectedBluetoothJson,
            updatedState.simulatedWifi,
            updatedState.mockCell,
            updatedState.simulatedBluetooth,
            updatedState.enableJitter
        )
        motionController.onStaticStarted(lat, lng)

        kotlinx.coroutines.delay(200)

        if (updatedState.restartAppsOnSpoof) {
            restartHookedAppsSilently()
        }

        _uiState.update {
            it.copy(isSpoofingActive = true, isSavingConfig = false)
        }
    }
}

private suspend fun MainViewModel.restartHookedAppsSilently() {
    val toKill = if (BuildConfig.GLOBAL_SCHEME) {
        systemHookRestartTargets()
    } else {
        lsposedManager.getHookedApps(context).map { it.packageName }
    }
    if (toKill.isNotEmpty()) {
        locationRepository.checkRootAccess()
        locationRepository.forceStopApps(toKill)
    }
}

internal fun MainViewModel.stopSpoofing() {
    settingsRepository.isSpoofingActive = false
    locationSyncJob?.cancel()
    locationSyncJob = null
    motionController.onStopped()
    viewModelScope.launch {
        locationRepository.stopSpoofing(context)
        _uiState.update {
            it.copy(isSpoofingActive = false)
        }
    }
}

internal fun MainViewModel.ensureOverlayPermission(): Boolean {
    if (android.provider.Settings.canDrawOverlays(context)) return true
    android.widget.Toast.makeText(
        context,
        context.getString(R.string.overlay_permission_required),
        android.widget.Toast.LENGTH_LONG
    ).show()
    context.startActivity(
        android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    return false
}

internal fun MainViewModel.toggleMockWifi() {
    val newVal = !_uiState.value.mockWifi
    settingsRepository.mockWifi = newVal
    _uiState.update { it.copy(mockWifi = newVal) }
    syncMockSettings()
}

internal fun MainViewModel.toggleMockCell() {
    val newVal = !_uiState.value.mockCell
    settingsRepository.mockCell = newVal
    _uiState.update { it.copy(mockCell = newVal) }
    syncMockSettings()
}

internal fun MainViewModel.toggleMockBluetooth() {
    val newVal = !_uiState.value.mockBluetooth
    settingsRepository.mockBluetooth = newVal
    _uiState.update { it.copy(mockBluetooth = newVal) }
    syncMockSettings()
}

internal fun MainViewModel.toggleEnableJitter() {
    val newVal = !_uiState.value.enableJitter
    settingsRepository.enableJitter = newVal
    _uiState.update { it.copy(enableJitter = newVal) }
    syncMockSettings()
}

internal fun MainViewModel.toggleRestartAppsOnSpoof() {
    val newVal = !_uiState.value.restartAppsOnSpoof
    settingsRepository.restartAppsOnSpoof = newVal
    _uiState.update { it.copy(restartAppsOnSpoof = newVal) }
}

private fun MainViewModel.syncMockSettings() {
    if (!_uiState.value.isSpoofingActive) return
    val state = _uiState.value
    viewModelScope.launch {
        locationRepository.patchConfig { json ->
            json.put("mock_wifi", state.simulatedWifi)
            json.put("mock_cell", state.mockCell)
            json.put("mock_bluetooth", state.simulatedBluetooth)
            json.put("enable_jitter", state.enableJitter)
        }
    }
}

internal fun MainViewModel.toggleContinuousScanning() {
    val state = _uiState.value
    if (state.isStoppingCollection || state.pendingCollectionLocations.isNotEmpty() || state.pendingCollectionRoute != null) return
    if (state.isSpoofingActive) {
        AppSnackbar.show(context.getString(R.string.stop_spoofing_before_scan))
        return
    }

    if (state.isContinuousScanning) {
        _uiState.update { it.copy(isContinuousScanning = false, isStoppingCollection = true) }
        continuousScanJob?.cancel()
        return
    }

    if (state.isRouteCollection && (state.isDrawingCollectionRoute || state.collectionRoutePoints.size < 2)) return

    _uiState.update {
        it.copy(
            isContinuousScanning = true,
            collectionIssue = null,
            scannedPointCount = 0,
            scannedWifiCount = 0,
            scannedCellCount = 0,
            scannedBluetoothCount = 0
        )
    }
    continuousScanJob = viewModelScope.launch(Dispatchers.IO) {
        val saved = linkedMapOf<Long, io.github.amahteru.locationspoofer.data.db.CompleteLocation>()
        var routeId: Long? = null
        try {
            while (isActive) {
                val realLoc = fetchRealLocationSilent(context)
                val insideRoute = !state.isRouteCollection || (realLoc != null &&
                    io.github.amahteru.locationspoofer.utils.PolylineCoverage.distanceMeters(
                        io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng(realLoc.first, realLoc.second),
                        state.collectionRoutePoints.map { io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng(it.lat, it.lng) }
                    ) <= EnvironmentResolver.COVERAGE_RADIUS_M)
                _uiState.update {
                    it.copy(collectionIssue = when {
                        realLoc == null -> CollectionIssue.NO_LOCATION
                        !insideRoute -> CollectionIssue.OFF_ROUTE
                        else -> null
                    })
                }
                if (realLoc != null && insideRoute) {
                    val lat = realLoc.first
                    val lng = realLoc.second
                    withContext(NonCancellable) {
                        if (state.isRouteCollection && routeId == null) {
                            routeId = environmentDao.insertCollectionRoute(
                                io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord(
                                    name = context.getString(R.string.collection_route_default_name,
                                        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())),
                                    pointsJson = kotlinx.serialization.json.Json.encodeToString(
                                        kotlinx.serialization.builtins.ListSerializer(io.github.amahteru.locationspoofer.data.model.RoutePoint.serializer()),
                                        state.collectionRoutePoints),
                                    halfWidthM = EnvironmentResolver.COVERAGE_RADIUS_M
                                )
                            )
                        }
                        val wifiJson = environmentScanner.scanWifi()
                        val cellJson = environmentScanner.scanCell()
                        val bluetoothJson = environmentScanner.scanBluetooth()
                        val id = saveEnvironmentData(lat, lng, wifiJson, cellJson, bluetoothJson, mergeNearby = true, collectionRouteId = routeId)
                        environmentDao.getCompleteLocationById(id)?.let { saved[id] = it }
                        val counts = EnvironmentResolver.signalCounts(saved.values.toList())
                        _uiState.update {
                            it.copy(
                                scannedPointCount = saved.size,
                                scannedWifiCount = counts.wifi,
                                scannedCellCount = counts.cell,
                                scannedBluetoothCount = counts.bluetooth
                            )
                        }
                    }
                }
                delay(10_000)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error.printStackTrace()
            withContext(Dispatchers.Main) {
                AppSnackbar.show(context.getString(R.string.collection_save_failed), long = true)
            }
        } finally {
            withContext(NonCancellable) {
                val savedRoute = routeId?.let { id ->
                    if (saved.isEmpty()) {
                        environmentDao.deleteCollectionRoute(id)
                        null
                    } else runCatching { environmentDao.getCollectionRoute(id)?.route }.getOrNull()
                }
                withContext(Dispatchers.Main) {
                    val requestedStop = _uiState.value.isStoppingCollection
                    continuousScanJob = null
                    _uiState.update {
                        it.copy(
                            isContinuousScanning = false,
                            isStoppingCollection = false,
                            collectionIssue = null,
                            pendingCollectionLocations = if (routeId == null) saved.values.map { record -> record.location } else emptyList(),
                            pendingCollectionRoute = savedRoute,
                            collectionRoutePoints = if (savedRoute != null) emptyList() else it.collectionRoutePoints,
                            isDrawingCollectionRoute = it.isDrawingCollectionRoute || savedRoute != null
                        )
                    }
                    if (requestedStop && saved.isEmpty()) {
                        AppSnackbar.show(context.getString(R.string.collection_no_saved_points), long = true)
                    }
                }
            }
        }
    }
}

internal fun MainViewModel.skipCollectionInfo() {
    if (_uiState.value.isSavingCollectionInfo) return
    _uiState.update { it.copy(pendingCollectionLocations = it.pendingCollectionLocations.drop(1)) }
}

internal fun MainViewModel.skipAllCollectionInfo() {
    if (!_uiState.value.isSavingCollectionInfo) _uiState.update { it.copy(pendingCollectionLocations = emptyList()) }
}

internal fun MainViewModel.saveCollectionInfo(locationId: Long, placeName: String, remark: String) {
    val state = _uiState.value
    if (state.isSavingCollectionInfo || state.pendingCollectionLocations.firstOrNull()?.id != locationId) return
    _uiState.update { it.copy(isSavingCollectionInfo = true) }
    viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                environmentDao.updateBasicInfo(locationId, placeName.trim(), remark.trim())
            }
            _uiState.update {
                it.copy(
                    isSavingCollectionInfo = false,
                    pendingCollectionLocations = it.pendingCollectionLocations.drop(1),
                    pinnedLocationName = if (it.pinnedCollectedLocationId == locationId) {
                        placeName.trim().ifBlank { remark.trim() }.ifBlank { it.pinnedLocationName }
                    } else it.pinnedLocationName
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _uiState.update { it.copy(isSavingCollectionInfo = false) }
            AppSnackbar.show(context.getString(R.string.collection_info_save_failed), long = true)
        }
    }
}
