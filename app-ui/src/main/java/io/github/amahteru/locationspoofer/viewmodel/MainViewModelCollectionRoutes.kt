package io.github.amahteru.locationspoofer.viewmodel

import io.github.amahteru.locationspoofer.ui.components.AppSnackbar
import androidx.lifecycle.viewModelScope
import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.data.model.EnvironmentResolver
import io.github.amahteru.locationspoofer.data.model.RoutePoint
import io.github.amahteru.locationspoofer.data.model.geometry
import io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng
import io.github.amahteru.locationspoofer.utils.GeoMath
import io.github.amahteru.locationspoofer.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MainViewModel.setRouteCollection(enabled: Boolean) {
    if (_uiState.value.isContinuousScanning || _uiState.value.isStoppingCollection) return
    _uiState.update { it.copy(isRouteCollection = enabled, isDrawingCollectionRoute = enabled && it.collectionRoutePoints.size < 2) }
}

internal fun MainViewModel.addCollectionRoutePoint(lat: Double, lng: Double) {
    if (_uiState.value.isContinuousScanning || _uiState.value.isStoppingCollection) return
    val previous = _uiState.value.collectionRoutePoints.lastOrNull()
    if (previous != null && GeoMath.distance(LatLng(previous.lat, previous.lng), LatLng(lat, lng)) < 2.0) return
    _uiState.update { it.copy(collectionRoutePoints = it.collectionRoutePoints + RoutePoint(lat, lng)) }
}

internal fun MainViewModel.undoCollectionRoutePoint() {
    if (!_uiState.value.isContinuousScanning && !_uiState.value.isStoppingCollection) {
        _uiState.update { it.copy(collectionRoutePoints = it.collectionRoutePoints.dropLast(1)) }
    }
}

internal fun MainViewModel.drawCollectionRoute() {
    if (_uiState.value.isContinuousScanning || _uiState.value.isStoppingCollection) return
    _uiState.update { it.copy(isRouteCollection = true, isDrawingCollectionRoute = true) }
}

internal fun MainViewModel.finishCollectionRouteDrawing() {
    if (_uiState.value.collectionRoutePoints.size >= 2) {
        _uiState.update { it.copy(isDrawingCollectionRoute = false) }
    }
}

internal fun MainViewModel.saveCollectionRouteInfo(id: Long, name: String, remark: String, closePending: Boolean = true, onSaved: () -> Unit = {}) {
    if (_uiState.value.isSavingCollectionInfo || name.isBlank()) return
    _uiState.update { it.copy(isSavingCollectionInfo = true) }
    viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { environmentDao.updateCollectionRouteInfo(id, name.trim(), remark.trim()) }
            _uiState.update {
                it.copy(isSavingCollectionInfo = false, pendingCollectionRoute = if (closePending) null else it.pendingCollectionRoute)
            }
            onSaved()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _uiState.update { it.copy(isSavingCollectionInfo = false) }
            AppSnackbar.show(context.getString(R.string.collection_info_save_failed), long = true)
        }
    }
}

internal fun MainViewModel.skipCollectionRouteInfo() {
    if (!_uiState.value.isSavingCollectionInfo) _uiState.update { it.copy(pendingCollectionRoute = null) }
}

internal fun MainViewModel.selectCollectedRoute(id: Long) {
    val route = collectionRoutes.value.firstOrNull { it.route.id == id } ?: return
    val points = route.route.geometry().takeIf { it.isNotEmpty() } ?: return
    pinnedLocationRecordId = null
    _uiState.update {
        it.copy(selectedCollectionRouteId = id, pinnedCollectedLocationId = null, pinnedLocationName = null,
            routePoints = points.map { p -> RoutePoint(p.lat, p.lng) },
            routePlanStage = io.github.amahteru.locationspoofer.data.model.RoutePlanStage.READY,
            useRealRoute = false)
    }
    evaluateMockCapabilities()
}

internal fun MainViewModel.deleteCollectedRoute(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { environmentDao.deleteCollectionRoute(id) }
    _uiState.update { if (it.selectedCollectionRouteId == id) it.copy(selectedCollectionRouteId = null) else it }
}

internal fun MainViewModel.environmentRecordsAt(lat: Double, lng: Double): List<CompleteLocation> =
    EnvironmentResolver.recordsAt(
        environmentLocations.value,
        collectionRoutes.value,
        lat,
        lng,
        _uiState.value.selectedCollectionRouteId
    )

internal fun MainViewModel.prepareRouteSimulationOptions(onReady: () -> Unit) {
    val point = _uiState.value.routePoints.firstOrNull() ?: return
    routeOptionsBackup = _uiState.value
    viewModelScope.launch {
        _uiState.update { it.copy(latitudeInput = point.lat.toString(), longitudeInput = point.lng.toString()) }
        evaluateMockCapabilitiesSuspend(point.lat, point.lng)
        onReady()
    }
}

internal fun MainViewModel.cancelRouteSimulationOptions() {
    val backup = routeOptionsBackup ?: return
    routeOptionsBackup = null
    settingsRepository.mockWifi = backup.mockWifi
    settingsRepository.mockCell = backup.mockCell
    settingsRepository.mockBluetooth = backup.mockBluetooth
    _uiState.update {
        it.copy(
            latitudeInput = backup.latitudeInput,
            longitudeInput = backup.longitudeInput,
            mockWifi = backup.mockWifi,
            mockCell = backup.mockCell,
            mockBluetooth = backup.mockBluetooth
        )
    }
    evaluateMockCapabilities()
}
