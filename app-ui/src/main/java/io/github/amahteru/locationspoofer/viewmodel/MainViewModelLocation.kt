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

internal suspend fun MainViewModel.evaluateMockCapabilitiesSuspend(lat: Double, lng: Double) {
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

    val validRecords = environmentRecordsAt(lat, lng).toMutableList()

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

internal suspend fun MainViewModel.fetchWifiFromWigleSync(lat: Double, lng: Double) {
    val settingsToken = settingsRepository.getWigleApiToken()
    if (settingsToken.isBlank()) {
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    canMockWifi = false
                )
            }
        }
        return
    }

    val wgs84 = io.github.amahteru.locationspoofer.utils.CoordinateUtils.gcj02ToWgs84(lat, lng)
    val wgsLat = wgs84.lat
    val wgsLng = wgs84.lng

    try {
        val rawJsonArrayString = wifiRepository.fetchWifiData(wgsLat, wgsLng, settingsToken)
        val nearbyArr = org.json.JSONArray(rawJsonArrayString)
        if (nearbyArr.length() > 0) {
            val wifiObj = org.json.JSONObject()
            wifiObj.put("isConnected", true)

            val firstAp = nearbyArr.getJSONObject(0)
            val firstBssid = firstAp.optString("bssid")
            val firstSsid = firstAp.optString("ssid")

            val connObj = org.json.JSONObject().apply {
                put("bssid", firstBssid)
                put("ssid", firstSsid)
                put(
                    "vendor",
                    io.github.amahteru.locationspoofer.utils.MacVendorHelper.getVendor(firstBssid)
                )
                put("level", -45)
                put("frequency", 2412)
                put("channel", 1)
                put("capabilities", "[WPA2-PSK-CCMP][ESS]")
                put("macAddress", "02:00:00:00:00:00")
                put("linkSpeed", 150)
                put("networkId", 1)
                put("wifiStandard", 4)
            }
            wifiObj.put("connectedWifi", connObj)

            val formattedNearby = org.json.JSONArray()
            for (i in 0 until nearbyArr.length()) {
                val ap = nearbyArr.getJSONObject(i)
                val bssid = ap.optString("bssid")
                val ssid = ap.optString("ssid")
                val level = -50 - (i * 2)
                val freq = if (i % 2 == 0) 2412 else 5180

                val itemObj = org.json.JSONObject().apply {
                    put("bssid", bssid)
                    put("ssid", ssid)
                    put(
                        "vendor",
                        io.github.amahteru.locationspoofer.utils.MacVendorHelper.getVendor(bssid)
                    )
                    put("level", level)
                    put("capabilities", "[WPA2-PSK-CCMP][ESS]")
                    put("frequency", freq)
                    put(
                        "channel",
                        io.github.amahteru.locationspoofer.utils.MacVendorHelper.frequencyToChannel(
                            freq
                        )
                    )
                }
                formattedNearby.put(itemObj)
            }
            wifiObj.put("nearbyWifi", formattedNearby)

            val formattedWifiJson = wifiObj.toString()

            withContext(Dispatchers.IO) {
                val id = saveEnvironmentData(lat, lng, formattedWifiJson, "[]", "{}")
                val latLngPrefix = context.getString(R.string.lat_lng_format_prefix)
                environmentDao.updateMetadata(
                    id,
                    lat,
                    lng,
                    context.getString(R.string.wigle_import),
                    "$latLngPrefix (${String.format(java.util.Locale.US, "%.6f", lat)}, ${
                        String.format(
                            java.util.Locale.US, "%.6f",
                            lng
                        )
                    })",
                    null, null, null
                )
            }

            withContext(Dispatchers.Main) {
                evaluateMockCapabilities()
            }
        } else {
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        canMockWifi = false
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    canMockWifi = false
                )
            }
        }
    }
}

internal suspend fun MainViewModel.fetchCellFromOpenCellIdSync(lat: Double, lng: Double) {
    val tokenToUse = settingsRepository.getOpencellidApiToken()
    if (tokenToUse.isBlank()) {
        return
    }
    val wgs84 = io.github.amahteru.locationspoofer.utils.CoordinateUtils.gcj02ToWgs84(lat, lng)
    val wgsLat = wgs84.lat
    val wgsLng = wgs84.lng

    try {
        val rawJsonArrayString = opencellidClient.fetchCellData(wgsLat, wgsLng, tokenToUse)
        val cellsArray = org.json.JSONArray(rawJsonArrayString)
        if (cellsArray.length() > 0) {
            val formattedCells = normalizeCellArrayForStorage(cellsArray)
            if (formattedCells.length() == 0) {
                return
            }

            withContext(Dispatchers.IO) {
                val id = saveEnvironmentData(lat, lng, "{}", formattedCells.toString(), "[]")
                val latLngPrefix = context.getString(R.string.lat_lng_format_prefix)
                environmentDao.updateMetadata(
                    id,
                    lat,
                    lng,
                    context.getString(R.string.opencellid_import),
                    "$latLngPrefix (${String.format(java.util.Locale.US, "%.6f", lat)}, ${
                        String.format(
                            java.util.Locale.US, "%.6f",
                            lng
                        )
                    })",
                    null, null, null
                )
            }

            withContext(Dispatchers.Main) {
                evaluateMockCapabilities()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
