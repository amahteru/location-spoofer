package io.github.amahteru.locationspoofer.viewmodel

import io.github.amahteru.locationspoofer.data.db.displayName
import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.data.model.EnvironmentResolver
import io.github.amahteru.locationspoofer.ui.components.AppSnackbar
import android.content.Context
import java.util.Locale
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng
import io.github.amahteru.locationspoofer.utils.GeoMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private fun oneShotLocationOption() = AMapLocationClientOption().apply {
    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
    isOnceLocation = true
    isNeedAddress = false
    isLocationCacheEnable = false
}

internal fun MainViewModel.fetchCurrentLocation(ctx: Context, forceCallback: ((Double, Double) -> Unit)? = null) {
    viewModelScope.launch(Dispatchers.Main) {
        val client = try {
            AMapLocationClient(ctx.applicationContext)
        } catch (e: Exception) {
            fallbackToNativeLocation(ctx, forceCallback, true)
            return@launch
        }
        client.setLocationOption(oneShotLocationOption())
        client.setLocationListener { loc ->
            if (loc != null && loc.errorCode == 0) {
                if (_uiState.value.longitudeInput.isEmpty() || _uiState.value.latitudeInput.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            latitudeInput = String.format(java.util.Locale.US, "%.6f", loc.latitude),
                            longitudeInput = String.format(java.util.Locale.US, "%.6f", loc.longitude),
                            showCoordinateError = false
                        )
                    }
                }
                forceCallback?.invoke(loc.latitude, loc.longitude)
            } else {
                fallbackToNativeLocation(ctx, forceCallback, true)
            }
            client.stopLocation()
            client.onDestroy()
        }
        client.startLocation()
    }
}

@android.annotation.SuppressLint("MissingPermission")
private fun MainViewModel.fallbackToNativeLocation(
    ctx: Context,
    forceCallback: ((Double, Double) -> Unit)?,
    convertToGcj: Boolean
) {
    try {
        val locationManager =
            ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val provider =
            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                android.location.LocationManager.NETWORK_PROVIDER
            } else {
                android.location.LocationManager.GPS_PROVIDER
            }

        locationManager.getCurrentLocation(provider, null, ctx.mainExecutor) { location ->
            if (location != null) applyNativeLocation(ctx, location, forceCallback, convertToGcj)
            else if (forceCallback != null) AppSnackbar.show(ctx.getString(io.github.amahteru.locationspoofer.ui.R.string.location_unavailable), long = true)
        }
    } catch (e: SecurityException) {
        if (forceCallback != null) {
            AppSnackbar.show(ctx.getString(io.github.amahteru.locationspoofer.ui.R.string.location_permission_denied), long = true)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun MainViewModel.applyNativeLocation(
    ctx: Context,
    location: android.location.Location,
    forceCallback: ((Double, Double) -> Unit)?,
    convertToGcj: Boolean
) {
    var finalLat = location.latitude
    var finalLng = location.longitude

    if (convertToGcj) {
        val converter = com.amap.api.maps.CoordinateConverter(ctx).apply {
            from(com.amap.api.maps.CoordinateConverter.CoordType.GPS)
            coord(com.amap.api.maps.model.LatLng(location.latitude, location.longitude))
        }
        val gcj = converter.convert()
        finalLat = gcj.latitude
        finalLng = gcj.longitude
    }

    if (_uiState.value.longitudeInput.isEmpty() || _uiState.value.latitudeInput.isEmpty()) {
        _uiState.update {
            it.copy(
                latitudeInput = String.format(java.util.Locale.US, "%.6f", finalLat),
                longitudeInput = String.format(java.util.Locale.US, "%.6f", finalLng),
                showCoordinateError = false
            )
        }
    }
    forceCallback?.invoke(finalLat, finalLng)
}

internal suspend fun MainViewModel.fetchRealLocationSilent(ctx: Context): Pair<Double, Double>? {
    val amapResult = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
            val client = try {
                AMapLocationClient(ctx.applicationContext)
            } catch (error: Exception) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            fun closeClient() {
                runCatching { client.stopLocation(); client.onDestroy() }
            }
            cont.invokeOnCancellation { closeClient() }
            client.setLocationOption(oneShotLocationOption())
            client.setLocationListener { location ->
                closeClient()
                if (cont.isActive) {
                    cont.resume(if (location != null && location.errorCode == 0) {
                        location.latitude to location.longitude
                    } else null)
                }
            }
            if (cont.isActive) client.startLocation() else closeClient()
        }
    }
    return amapResult ?: fallbackToNativeLocationSilent(ctx, true)
}

@android.annotation.SuppressLint("MissingPermission")
private suspend fun MainViewModel.fallbackToNativeLocationSilent(
    ctx: Context,
    convertToGcj: Boolean
): Pair<Double, Double>? = withTimeoutOrNull(5_000) {
    suspendCancellableCoroutine { cont ->
        val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val cancellation = android.os.CancellationSignal()
        cont.invokeOnCancellation { cancellation.cancel() }
        try {
            val provider = when {
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) -> android.location.LocationManager.NETWORK_PROVIDER
                locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) -> android.location.LocationManager.GPS_PROVIDER
                else -> {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
            }
            val lastLocation = locationManager.getLastKnownLocation(provider)
            if (lastLocation != null) {
                cont.resume(getNativeConverted(ctx, lastLocation, convertToGcj))
                return@suspendCancellableCoroutine
            }
            locationManager.getCurrentLocation(provider, cancellation, ctx.mainExecutor) { location ->
                if (cont.isActive) cont.resume(location?.let { getNativeConverted(ctx, it, convertToGcj) })
            }
        } catch (error: Exception) {
            cancellation.cancel()
            if (cont.isActive) cont.resume(null)
        }
    }
}

private fun MainViewModel.getNativeConverted(
    ctx: Context,
    location: android.location.Location,
    convertToGcj: Boolean
): Pair<Double, Double> {
    var finalLat = location.latitude
    var finalLng = location.longitude
    if (convertToGcj) {
        val converter = com.amap.api.maps.CoordinateConverter(ctx).apply {
            from(com.amap.api.maps.CoordinateConverter.CoordType.GPS)
            coord(com.amap.api.maps.model.LatLng(location.latitude, location.longitude))
        }
        val gcj = converter.convert()
        finalLat = gcj.latitude
        finalLng = gcj.longitude
    }
    return Pair(finalLat, finalLng)
}

internal fun MainViewModel.updateLongitude(value: String) {
    if (isValidCoord(value)) {
        _uiState.update { it.copy(longitudeInput = value, showCoordinateError = false) }
        evaluateMockCapabilities()
    }
}

internal fun MainViewModel.updateLatitude(value: String) {
    if (isValidCoord(value)) {
        _uiState.update { it.copy(latitudeInput = value, showCoordinateError = false) }
        evaluateMockCapabilities()
    }
}

private fun MainViewModel.isValidCoord(value: String): Boolean {
    if (value.isEmpty() || value == "-") return true
    return value.toDoubleOrNull() != null
}

internal fun MainViewModel.evaluateMockCapabilities() {
    mockCapabilitiesJob?.cancel()
    val state = _uiState.value
    val lat = state.latitudeInput.toDoubleOrNull()
    val lng = state.longitudeInput.toDoubleOrNull()

    if (lat == null || lng == null) {
        _uiState.update {
            it.copy(
                canMockWifi = false,
                canMockCell = false,
                canMockBluetooth = false,
                collectedWifiJson = "{}",
                collectedCellJson = "[]",
                collectedBluetoothJson = "[]",
            )
        }
        return
    }

    mockCapabilitiesJob = viewModelScope.launch {
        evaluateMockCapabilitiesSuspend(lat, lng)
    }
}

internal fun MainViewModel.selectCollectedLocation(locationId: Long, announce: Boolean = false) {
    viewModelScope.launch {
        val record = withContext(Dispatchers.IO) {
            environmentDao.getCompleteLocationById(locationId)
        }
        if (record != null) {
            pinnedLocationRecordId = locationId
            val name = record.location.displayName()
            _uiState.update {
                it.copy(
                    latitudeInput = record.location.lat.toString(),
                    longitudeInput = record.location.lng.toString(),
                    selectedCollectionRouteId = null,
                    pinnedCollectedLocationId = locationId,
                    pinnedLocationName = name
                )
            }
            evaluateMockCapabilitiesSuspend(record.location.lat, record.location.lng)
            if (announce) AppSnackbar.show(context.getString(R.string.pinned_collected_location_toast, name))
        }
    }
}

internal fun MainViewModel.clearPinnedCollectedLocation() {
    pinnedLocationRecordId = null
    _uiState.update {
        it.copy(
            pinnedCollectedLocationId = null,
            pinnedLocationName = null
        )
    }
    evaluateMockCapabilities()
}

internal suspend fun MainViewModel.evaluateMockCapabilitiesSuspend(lat: Double, lng: Double, fromDatabase: Boolean = false) {
    val currentPinnedId = pinnedLocationRecordId
    var pinnedRecord: CompleteLocation? = null

    if (currentPinnedId != null) {
        val pinned = withContext(Dispatchers.IO) {
            environmentDao.getCompleteLocationById(currentPinnedId)
        }
        if (pinned != null) {
            val distToPinned = GeoMath.distance(LatLng(lat, lng), LatLng(pinned.location.lat, pinned.location.lng))
            if (distToPinned <= EnvironmentResolver.COVERAGE_RADIUS_M) {
                pinnedRecord = pinned
            } else {
                pinnedLocationRecordId = null
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            pinnedCollectedLocationId = null,
                            pinnedLocationName = null
                        )
                    }
                }
            }
        } else {
            pinnedLocationRecordId = null
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        pinnedCollectedLocationId = null,
                        pinnedLocationName = null
                    )
                }
            }
        }
    }

    val records = if (fromDatabase) {
        withContext(Dispatchers.IO) {
            EnvironmentResolver.recordsAt(
                environmentDao.getAllCompleteLocations(),
                environmentDao.getCollectionRoutes(),
                lat,
                lng,
                _uiState.value.selectedCollectionRouteId
            )
        }
    } else {
        environmentRecordsAt(lat, lng)
    }
    val validRecords = records.toMutableList()

    if (pinnedRecord != null) {
        validRecords.removeAll { it.location.id == pinnedRecord.location.id }
        validRecords.add(0, pinnedRecord)
    }

    withContext(Dispatchers.Main) {
        val current = _uiState.value
        if (current.latitudeInput.toDoubleOrNull() != lat || current.longitudeInput.toDoubleOrNull() != lng) {
            return@withContext
        }
        applyEnvironment(validRecords, lat, lng)
    }
}

internal fun MainViewModel.applyEnvironment(records: List<CompleteLocation>, lat: Double, lng: Double) {
    val (wifi, cell, bluetooth) = EnvironmentResolver.toJson(records, lat, lng, pinnedLocationRecordId)
    _uiState.update {
        it.copy(
            canMockWifi = EnvironmentResolver.hasWifi(records),
            canMockCell = EnvironmentResolver.hasCell(records),
            canMockBluetooth = EnvironmentResolver.hasBluetooth(records),
            collectedWifiJson = wifi,
            collectedCellJson = cell,
            collectedBluetoothJson = bluetooth,
        )
    }
}

internal suspend fun MainViewModel.fetchMissingEnvironment(lat: Double, lng: Double, mockWifi: Boolean, mockCell: Boolean): Boolean {
    val records = environmentRecordsAt(lat, lng)
    val wifiSaved = mockWifi && !EnvironmentResolver.hasWifi(records) && fetchWifiFromWigleSync(lat, lng)
    val cellSaved = mockCell && !EnvironmentResolver.hasCell(records) && fetchCellFromOpenCellIdSync(lat, lng)
    return wifiSaved || cellSaved
}

private fun MainViewModel.importRemark(lat: Double, lng: Double): String =
    String.format(java.util.Locale.US, "%s (%.6f, %.6f)", context.getString(R.string.lat_lng_format_prefix), lat, lng)

private fun wifiChannelFrequency(channel: Int): Int =
    if (channel <= 14) 2407 + channel * 5 else 5000 + channel * 5

private suspend fun MainViewModel.fetchWifiFromWigleSync(lat: Double, lng: Double): Boolean {
    val token = settingsRepository.getWigleApiToken()
    if (token.isBlank()) return false

    val wgs84 = io.github.amahteru.locationspoofer.utils.CoordinateUtils.gcj02ToWgs84(lat, lng)
    return try {
        val nearbyArr = org.json.JSONArray(wifiRepository.fetchWifiData(wgs84.lat, wgs84.lng, token))
        if (nearbyArr.length() == 0) return false

        val formattedNearby = org.json.JSONArray()
        for (i in 0 until nearbyArr.length()) {
            val ap = nearbyArr.getJSONObject(i)
            val bssid = ap.getString("bssid")
            val channel = ap.getInt("channel")
            formattedNearby.put(org.json.JSONObject().apply {
                put("bssid", bssid)
                put("ssid", ap.getString("ssid"))
                put("vendor", io.github.amahteru.locationspoofer.utils.MacVendorHelper.getVendor(bssid))
                put("level", -50 - (i * 2))
                put("capabilities", "[WPA2-PSK-CCMP][ESS]")
                put("frequency", wifiChannelFrequency(channel))
                put("channel", channel)
            })
        }
        val wifiJson = org.json.JSONObject()
            .put("isConnected", false)
            .put("connectedWifi", org.json.JSONObject.NULL)
            .put("nearbyWifi", formattedNearby)
            .toString()

        withContext(Dispatchers.IO) {
            saveEnvironmentData(
                lat, lng, wifiJson, "[]", "[]",
                placeName = context.getString(R.string.wigle_import),
                remark = importRemark(lat, lng)
            )
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        AppSnackbar.show(context.getString(R.string.wigle_fetch_failed), long = true)
        false
    }
}

private suspend fun MainViewModel.fetchCellFromOpenCellIdSync(lat: Double, lng: Double): Boolean {
    val token = settingsRepository.getOpencellidApiToken()
    if (token.isBlank()) return false

    val wgs84 = io.github.amahteru.locationspoofer.utils.CoordinateUtils.gcj02ToWgs84(lat, lng)
    return try {
        val cellsArray = org.json.JSONArray(opencellidClient.fetchCellData(wgs84.lat, wgs84.lng, token))
        val formattedCells = normalizeCellArrayForStorage(cellsArray)
        if (formattedCells.length() == 0) return false

        withContext(Dispatchers.IO) {
            saveEnvironmentData(
                lat, lng, "{}", formattedCells.toString(), "[]",
                placeName = context.getString(R.string.opencellid_import),
                remark = importRemark(lat, lng)
            )
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        AppSnackbar.show(context.getString(R.string.opencellid_fetch_failed), long = true)
        false
    }
}
