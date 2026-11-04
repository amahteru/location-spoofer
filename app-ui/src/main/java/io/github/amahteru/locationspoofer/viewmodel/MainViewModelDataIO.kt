package io.github.amahteru.locationspoofer.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.amahteru.locationspoofer.data.db.LocationRecord
import io.github.amahteru.locationspoofer.data.model.ImportExportCounts
import io.github.amahteru.locationspoofer.data.model.ImportExportSelection
import io.github.amahteru.locationspoofer.data.model.AppMapType
import io.github.amahteru.locationspoofer.data.model.EnvironmentResolver
import io.github.amahteru.locationspoofer.data.model.MapEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

internal suspend fun MainViewModel.saveEnvironmentData(
    lat: Double,
    lng: Double,
    wifiJson: String,
    cellJson: String,
    bluetoothJson: String,
    mergeNearby: Boolean = false,
    collectionRouteId: Long? = null
): Long {
    val existingLocation = if (mergeNearby) null else environmentDao.findLocationByCoordinates(lat, lng)
    val locId = if (mergeNearby) {
        environmentDao.findOrCreateCollectionLocation(lat, lng, collectionRouteId)
    } else if (existingLocation != null) {
        environmentDao.updateCollectionTimestamp(existingLocation.id, System.currentTimeMillis())
        existingLocation.id
    } else {
        environmentDao.insertLocation(
            io.github.amahteru.locationspoofer.data.db.LocationRecord(
                lat = lat,
                lng = lng
            )
        )
    }

    try {
        val wifiObj = org.json.JSONObject(wifiJson)
        val isConnected = wifiObj.optBoolean("isConnected", false)
        if (isConnected && wifiObj.has("connectedWifi")) {
            val conn = wifiObj.getJSONObject("connectedWifi")
            val connWifi = io.github.amahteru.locationspoofer.data.db.LocationConnectedWifi(
                locationId = locId,
                bssid = conn.optString("bssid"),
                ssid = conn.optString("ssid"),
                vendor = conn.optString("vendor"),
                macAddress = conn.optString("macAddress"),
                frequency = conn.optInt("frequency"),
                linkSpeed = conn.optInt("linkSpeed"),
                level = conn.optInt("level"),
                capabilities = conn.optString("capabilities"),
                networkId = conn.optInt("networkId"),
                wifiStandard = conn.optInt("wifiStandard")
            )
            environmentDao.insertConnectedWifi(connWifi)
        }

        val nearbyArr = wifiObj.optJSONArray("nearbyWifi")
        if (nearbyArr != null) {
            for (i in 0 until nearbyArr.length()) {
                val obj = nearbyArr.getJSONObject(i)
                val bssid = obj.optString("bssid")
                if (bssid.isEmpty()) continue
                environmentDao.insertWifiDevice(
                    io.github.amahteru.locationspoofer.data.db.WifiDevice(
                        bssid = bssid,
                        ssid = obj.optString("ssid", ""),
                        frequency = obj.optInt("frequency", 0),
                        capabilities = obj.optString("capabilities", ""),
                        vendor = obj.optString("vendor", "")
                    )
                )
                environmentDao.insertLocationWifi(
                    io.github.amahteru.locationspoofer.data.db.LocationWifi(
                        locationId = locId,
                        bssid = bssid,
                        level = obj.optInt("level", 0)
                    )
                )
            }
        }
    } catch (e: org.json.JSONException) {
        e.printStackTrace()
    }

    try {
        val cellArr = org.json.JSONArray(cellJson)
        for (i in 0 until cellArr.length()) {
            val obj = cellArr.getJSONObject(i)
            val type =
                normalizeCellType(obj.optString("type", obj.optString("radio", "UNKNOWN")))
            val area = cellArea(obj)
            val identity = cellIdentity(obj)
            val tac = when (type) {
                "LTE", "NR" -> area
                else -> positiveCellInt(obj, "tac", default = 0)
            }
            val lac = when (type) {
                "GSM", "WCDMA" -> area
                else -> positiveCellInt(obj, "lac", default = 0)
            }
            val ci = when (type) {
                "LTE" -> identity
                else -> positiveCellInt(obj, "ci", default = 0)
            }
            val cid = when (type) {
                "GSM", "WCDMA" -> identity
                else -> positiveCellInt(obj, "cid", default = 0)
            }
            val nci = if (type == "NR") {
                obj.optLong("nci", identity.toLong()).takeIf { it > 0L } ?: identity.toLong()
            } else {
                obj.optLong("nci", 0L)
            }
            val basestationId = if (type == "CDMA") {
                positiveCellInt(obj, "basestationId", "cellid", "cell", default = 0)
            } else {
                positiveCellInt(obj, "basestationId", default = 0)
            }
            if (area <= 0 && identity <= 0 && basestationId <= 0) continue
            val cellKey = "${type}_${positiveCellInt(obj, "mcc", default = 460)}_${
                positiveCellInt(
                    obj,
                    "mnc",
                    "net",
                    default = 0
                )
            }_${tac}_${ci}_${cid}_${basestationId}_${nci}"
            val device = io.github.amahteru.locationspoofer.data.db.CellDevice(
                cellKey = cellKey, type = type,
                mcc = positiveCellInt(obj, "mcc", default = 460),
                mnc = positiveCellInt(obj, "mnc", "net", default = 0),
                tac = tac, ci = ci,
                pci = positiveCellInt(
                    obj,
                    "pci",
                    default = if (identity > 0) (identity % 504).coerceIn(0, 503) else 0
                ),
                lac = lac, cid = cid,
                psc = positiveCellInt(obj, "psc", default = 0),
                nci = nci,
                networkId = positiveCellInt(obj, "networkId", default = 0),
                systemId = positiveCellInt(obj, "systemId", default = 0),
                basestationId = basestationId
            )
            environmentDao.insertCellDevice(device)
            environmentDao.insertLocationCell(
                io.github.amahteru.locationspoofer.data.db.LocationCell(
                    locId,
                    cellKey,
                    cellSignalDbm(obj, i),
                    obj.optBoolean("isRegistered", i == 0)
                )
            )
        }
    } catch (e: org.json.JSONException) {
    }

    try {
        val btArr = org.json.JSONArray(bluetoothJson)
        for (i in 0 until btArr.length()) {
            val obj = btArr.getJSONObject(i)
            val address = obj.optString("address")
            if (address.isEmpty()) continue
            environmentDao.insertBluetoothDevice(
                io.github.amahteru.locationspoofer.data.db.BluetoothDevice(
                    address,
                    obj.optString("name", ""),
                    obj.optString("scanRecordHex", "")
                )
            )
            environmentDao.insertLocationBluetooth(
                io.github.amahteru.locationspoofer.data.db.LocationBluetooth(
                    locId,
                    address,
                    obj.optInt("rssi", -60)
                )
            )
        }
    } catch (e: org.json.JSONException) {
    }
    return locId
}

internal fun MainViewModel.locationToJson(
    records: List<io.github.amahteru.locationspoofer.data.db.CompleteLocation>,
    targetLat: Double,
    targetLng: Double
): Triple<String, String, String> =
    EnvironmentResolver.toJson(records, targetLat, targetLng, pinnedLocationRecordId)

internal fun MainViewModel.collectExportCounts(onResult: (ImportExportCounts) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        val counts = try {
            ImportExportCounts(
                locations = environmentDao.getAllCompleteLocations().count { it.location.collectionRouteId == null } + environmentDao.getCollectionRoutes().size,
                savedLocations = settingsRepository.getSavedLocations().size,
                savedRoutes = locationRepository.getAllSavedRoutesList().size,
                settings = 1,
                apiKeys = listOf(
                    settingsRepository.getAmapApiKey(),
                    settingsRepository.getBaiduApiKey(),
                    settingsRepository.getGoogleApiKey(),
                    settingsRepository.getWigleApiToken(),
                    settingsRepository.getOpencellidApiToken()
                ).count { it.isNotBlank() }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ImportExportCounts()
        }
        launch(Dispatchers.Main) { onResult(counts) }
    }
}

internal fun MainViewModel.exportEnvironmentData(
    uri: android.net.Uri,
    selection: ImportExportSelection = ImportExportSelection(),
    onResult: (Boolean) -> Unit = {}
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val locations = if (selection.locations) environmentDao.getAllCompleteLocations().filter { it.location.collectionRouteId == null } else emptyList()
            val collectionRoutes = if (selection.locations) environmentDao.getCollectionRoutes() else emptyList()
            val savedLocations = if (selection.savedLocations) settingsRepository.getSavedLocations() else emptyList()
            val savedRoutes = if (selection.savedRoutes) locationRepository.getAllSavedRoutesList() else emptyList()
            val settings = if (selection.settings) {
                io.github.amahteru.locationspoofer.data.db.ExportedSettings(
                    mockWifi = settingsRepository.mockWifi,
                    mockCell = settingsRepository.mockCell,
                    mockBluetooth = settingsRepository.mockBluetooth,
                    enableJitter = settingsRepository.enableJitter,
                    altitude = settingsRepository.altitude,
                    altitudeVariationM = settingsRepository.altitudeVariationM,
                    satelliteCount = settingsRepository.satelliteCount,
                    mapType = settingsRepository.getMapType(),
                    mapEngine = settingsRepository.getMapEngine()
                )
            } else null
            val apiKeys = if (selection.apiKeys) {
                io.github.amahteru.locationspoofer.data.db.ExportedApiKeys(
                    amapApiKey = settingsRepository.getAmapApiKey(),
                    baiduApiKey = settingsRepository.getBaiduApiKey(),
                    googleApiKey = settingsRepository.getGoogleApiKey(),
                    wigleApiToken = settingsRepository.getWigleApiToken(),
                    opencellidApiToken = settingsRepository.getOpencellidApiToken()
                )
            } else null

            val dataPackage = io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage(
                version = 4,
                exportTimestamp = System.currentTimeMillis(),
                appVersion = "2.0.0",
                locations = locations,
                collectionRoutes = collectionRoutes,
                savedLocations = savedLocations,
                savedRoutes = savedRoutes,
                settings = settings,
                apiKeys = apiKeys
            )

            val json = kotlinx.serialization.json.Json {
                prettyPrint = true
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
            val jsonStr = json.encodeToString(dataPackage)

            val bytes = jsonStr.toByteArray(Charsets.UTF_8)
            val written = try {
                context.contentResolver.openOutputStream(uri, "wt")
            } catch (e: Exception) {
                e.printStackTrace()
                context.contentResolver.openOutputStream(uri)
            }?.use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
                true
            } ?: false

            launch(Dispatchers.Main) { onResult(written) }
        } catch (e: Exception) {
            e.printStackTrace()
            launch(Dispatchers.Main) { onResult(false) }
        }
    }
}

internal fun MainViewModel.importEnvironmentData(uri: android.net.Uri, onComplete: (Int) -> Unit) {
    parseImportPackage(uri) { pkg ->
        if (pkg == null) onComplete(io.github.amahteru.locationspoofer.ui.R.string.import_parse_failed)
        else applyImportPackage(pkg, ImportExportSelection(), onComplete)
    }
}

internal fun MainViewModel.parseImportPackage(
    uri: android.net.Uri,
    onParsed: (io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage?) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        val parsed = try {
            parseImportPackageInternal(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        launch(Dispatchers.Main) { onParsed(parsed) }
    }
}

private fun MainViewModel.parseImportPackageInternal(
    uri: android.net.Uri
): io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage? {
    val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
        inputStream.bufferedReader().use { it.readText() }
    } ?: return null

    val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    return json.decodeFromString<io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage>(jsonStr)
}

internal fun MainViewModel.applyImportPackage(
    pkg: io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage,
    selection: ImportExportSelection,
    onComplete: (Int) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (selection.locations) {
                pkg.locations.forEach { environmentDao.insertCompleteLocationData(it) }
                pkg.collectionRoutes.forEach { environmentDao.insertCompleteCollectionRoute(it) }
            }

            if (selection.savedLocations && pkg.savedLocations.isNotEmpty()) {
                val currentSaved = settingsRepository.getSavedLocations().toMutableList()
                pkg.savedLocations.forEach { loc ->
                    if (currentSaved.none { it.lat == loc.lat && it.lng == loc.lng }) {
                        currentSaved.add(loc)
                    }
                }
                settingsRepository.setSavedLocations(currentSaved)
            }

            if (selection.savedRoutes && pkg.savedRoutes.isNotEmpty()) {
                pkg.savedRoutes.forEach { route ->
                    locationRepository.insertSavedRouteEntity(route.copy(id = 0))
                }
            }

            if (selection.settings) {
                pkg.settings?.let { s ->
                    settingsRepository.mockWifi = s.mockWifi
                    settingsRepository.mockCell = s.mockCell
                    settingsRepository.mockBluetooth = s.mockBluetooth
                    settingsRepository.enableJitter = s.enableJitter
                    if (s.altitude.isNotBlank()) settingsRepository.altitude = s.altitude
                    s.altitudeVariationM?.let { settingsRepository.altitudeVariationM = it }
                    if (s.satelliteCount.isNotBlank()) settingsRepository.satelliteCount = s.satelliteCount
                    if (s.mapType.isNotBlank()) settingsRepository.setMapType(s.mapType)
                    if (s.mapEngine.isNotBlank()) settingsRepository.setMapEngine(s.mapEngine)
                }
            }
            if (selection.apiKeys) {
                pkg.apiKeys?.let { k ->
                    if (k.amapApiKey.isNotBlank()) settingsRepository.setAmapApiKey(k.amapApiKey)
                    if (k.baiduApiKey.isNotBlank()) settingsRepository.setBaiduApiKey(k.baiduApiKey)
                    if (k.googleApiKey.isNotBlank()) settingsRepository.setGoogleApiKey(k.googleApiKey)
                    if (k.wigleApiToken.isNotBlank()) settingsRepository.setWigleApiToken(k.wigleApiToken)
                    if (k.opencellidApiToken.isNotBlank()) {
                        settingsRepository.setOpencellidApiToken(k.opencellidApiToken)
                    }
                }
            }

            val updatedSaved = settingsRepository.getSavedLocations()

            _uiState.update {
                it.copy(
                    savedLocations = updatedSaved,
                    mockWifi = settingsRepository.mockWifi,
                    mockCell = settingsRepository.mockCell,
                    mockBluetooth = settingsRepository.mockBluetooth,
                    enableJitter = settingsRepository.enableJitter,
                    altitudeInput = settingsRepository.altitude.ifBlank { it.altitudeInput },
                    altitudeVariationM = settingsRepository.altitudeVariationM,
                    satelliteCountInput = settingsRepository.satelliteCount.ifBlank { it.satelliteCountInput },
                    amapApiKey = settingsRepository.getAmapApiKey(),
                    baiduApiKey = settingsRepository.getBaiduApiKey(),
                    googleApiKey = settingsRepository.getGoogleApiKey(),
                    mapType = try {
                        AppMapType.valueOf(settingsRepository.getMapType())
                    } catch (e: Exception) {
                        it.mapType
                    },
                    mapEngine = try {
                        MapEngine.valueOf(settingsRepository.getMapEngine())
                    } catch (e: Exception) {
                        it.mapEngine
                    }
                )
            }

            launch(Dispatchers.Main) {
                onComplete(io.github.amahteru.locationspoofer.ui.R.string.import_merge_success)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            launch(Dispatchers.Main) { onComplete(io.github.amahteru.locationspoofer.ui.R.string.import_failed) }
        }
    }
}
