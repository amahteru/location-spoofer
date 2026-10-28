package io.github.amahteru.locationspoofer.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.amahteru.locationspoofer.data.db.LocationRecord
import io.github.amahteru.locationspoofer.data.model.ImportExportCounts
import io.github.amahteru.locationspoofer.data.model.ImportExportSelection
import io.github.amahteru.locationspoofer.data.model.SavedLocation
import io.github.amahteru.locationspoofer.data.model.AppMapType
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
): Triple<String, String, String> {
    if (records.isEmpty()) return Triple("{}", "[]", "[]")

    val weights = records.mapIndexed { i, it ->
        if (i == 0 && (it.location.id == pinnedLocationRecordId || it.location.selectedWifiBssid != null)) {
            1000.0
        } else {
            val rLat = Math.toRadians(it.location.lat - targetLat)
            val rLng = Math.toRadians(it.location.lng - targetLng)
            val rA = kotlin.math.sin(rLat / 2).let { v -> v * v } + kotlin.math.cos(
                Math.toRadians(targetLat)
            ) * kotlin.math.cos(Math.toRadians(it.location.lat)) * kotlin.math.sin(rLng / 2)
                .let { v -> v * v }
            val dist =
                2 * 6378137.0 * kotlin.math.atan2(kotlin.math.sqrt(rA), kotlin.math.sqrt(1 - rA))
            val safeDist = kotlin.math.max(dist, 1.0)
            1.0 / (safeDist * safeDist)
        }
    }

    val closestRecord = records.firstOrNull()
    val explicitWifiBssid = closestRecord?.location?.selectedWifiBssid
    val explicitWifi =
        if (explicitWifiBssid != null && explicitWifiBssid != "__NONE__") {
            closestRecord.wifis.find { it.device.bssid.equals(explicitWifiBssid, ignoreCase = true) }
        } else null

    val closestConnectedWifi = closestRecord?.connectedWifi
    val connectedObj = if (explicitWifiBssid == "__NONE__") {
        null
    } else if (explicitWifi != null) {
        org.json.JSONObject().apply {
            put("ssid", explicitWifi.device.ssid)
            put("bssid", explicitWifi.device.bssid)
            put("vendor", explicitWifi.device.vendor)
            put("macAddress", explicitWifi.device.bssid)
            put("frequency", explicitWifi.device.frequency)
            put(
                "channel",
                io.github.amahteru.locationspoofer.utils.MacVendorHelper.frequencyToChannel(
                    explicitWifi.device.frequency
                )
            )
            put("linkSpeed", 65)
            put("level", explicitWifi.locationWifi.level)
            put("capabilities", explicitWifi.device.capabilities)
            put("networkId", 1)
            put("wifiStandard", 6)
        }
    } else if (closestConnectedWifi != null && (explicitWifiBssid == null || explicitWifiBssid.equals(closestConnectedWifi.bssid, ignoreCase = true))) {
        val cw = closestConnectedWifi
        org.json.JSONObject().apply {
            put("ssid", cw.ssid)
            put("bssid", cw.bssid)
            put("vendor", cw.vendor)
            put("macAddress", cw.macAddress)
            put("frequency", cw.frequency)
            put(
                "channel",
                io.github.amahteru.locationspoofer.utils.MacVendorHelper.frequencyToChannel(cw.frequency)
            )
            put("linkSpeed", cw.linkSpeed)
            put("level", cw.level)
            put("capabilities", cw.capabilities)
            put("networkId", cw.networkId)
            put("wifiStandard", cw.wifiStandard)
        }
    } else {
        null
    }

    val wifiMap = mutableMapOf<String, io.github.amahteru.locationspoofer.data.db.LocationWithWifi>()
    val wifiLevels = mutableMapOf<String, Double>()
    val wifiWeights = mutableMapOf<String, Double>()

    records.forEachIndexed { i, rec ->
        rec.wifis.forEach { rw ->
            val bssid = rw.device.bssid
            if (!wifiMap.containsKey(bssid)) wifiMap[bssid] = rw
            wifiLevels[bssid] = (wifiLevels[bssid] ?: 0.0) + rw.locationWifi.level * weights[i]
            wifiWeights[bssid] = (wifiWeights[bssid] ?: 0.0) + weights[i]
        }
    }

    val nearbyArr = org.json.JSONArray()
    wifiMap.forEach { (bssid, rw) ->
        val w = wifiWeights[bssid]!!
        val interpolatedLevel = (wifiLevels[bssid]!! / w).toInt()
        val obj = org.json.JSONObject().apply {
            put("bssid", bssid)
            put("ssid", rw.device.ssid)
            put("vendor", rw.device.vendor)
            put("frequency", rw.device.frequency)
            put(
                "channel",
                io.github.amahteru.locationspoofer.utils.MacVendorHelper.frequencyToChannel(rw.device.frequency)
            )
            put("capabilities", rw.device.capabilities)
            put("level", interpolatedLevel)
        }
        nearbyArr.put(obj)
    }

    val hasConnected = connectedObj != null
    val wifiResultObj = org.json.JSONObject().apply {
        put("isConnected", hasConnected)
        put("connectedWifi", connectedObj ?: org.json.JSONObject.NULL)
        put("nearbyWifi", nearbyArr)
    }
    val wifiArr = wifiResultObj

    val cellMap = mutableMapOf<String, io.github.amahteru.locationspoofer.data.db.LocationWithCell>()
    val cellDbms = mutableMapOf<String, Double>()
    val cellWeights = mutableMapOf<String, Double>()

    records.forEachIndexed { i, rec ->
        rec.cells.forEach { rc ->
            val cellKey = rc.device.cellKey
            if (!cellMap.containsKey(cellKey)) cellMap[cellKey] = rc
            cellDbms[cellKey] = (cellDbms[cellKey] ?: 0.0) + rc.locationCell.dbm * weights[i]
            cellWeights[cellKey] = (cellWeights[cellKey] ?: 0.0) + weights[i]
        }
    }

    val explicitCellKey = closestRecord?.location?.selectedCellKey
    val cellArr = org.json.JSONArray()
    val cellList = mutableListOf<org.json.JSONObject>()
    cellMap.forEach { (cellKey, rc) ->
        val w = cellWeights[cellKey]!!
        val interpolatedDbm = (cellDbms[cellKey]!! / w).toInt()
        val obj = org.json.JSONObject()
        obj.put("type", rc.device.type)
        obj.put("mcc", rc.device.mcc)
        obj.put("mnc", rc.device.mnc)
        obj.put("tac", rc.device.tac)
        obj.put("ci", rc.device.ci)
        obj.put("pci", rc.device.pci)
        obj.put("lac", rc.device.lac)
        obj.put("cid", rc.device.cid)
        obj.put("psc", rc.device.psc)
        obj.put("nci", rc.device.nci)
        obj.put("networkId", rc.device.networkId)
        obj.put("systemId", rc.device.systemId)
        obj.put("basestationId", rc.device.basestationId)
        obj.put("dbm", interpolatedDbm)
        val isReg =
            if (explicitCellKey != null) cellKey.equals(explicitCellKey, ignoreCase = true) else rc.locationCell.isRegistered
        obj.put("isRegistered", isReg)
        if (isReg) {
            cellList.add(0, obj)
        } else {
            cellList.add(obj)
        }
    }
    cellList.forEach { cellArr.put(it) }

    val btMap =
        mutableMapOf<String, io.github.amahteru.locationspoofer.data.db.LocationWithBluetooth>()
    val btRssis = mutableMapOf<String, Double>()
    val btWeights = mutableMapOf<String, Double>()

    records.forEachIndexed { i, rec ->
        rec.bluetooths.forEach { rb ->
            val address = rb.device.address
            if (!btMap.containsKey(address)) btMap[address] = rb
            btRssis[address] =
                (btRssis[address] ?: 0.0) + rb.locationBluetooth.rssi * weights[i]
            btWeights[address] = (btWeights[address] ?: 0.0) + weights[i]
        }
    }

    val explicitBtAddress = closestRecord?.location?.selectedBluetoothAddress
    val btArr = org.json.JSONArray()
    val btList = mutableListOf<org.json.JSONObject>()
    btMap.forEach { (address, rb) ->
        val w = btWeights[address]!!
        val interpolatedRssi = (btRssis[address]!! / w).toInt()
        val obj = org.json.JSONObject()
        obj.put("address", address)
        obj.put("name", rb.device.name)
        obj.put("scanRecordHex", rb.device.scanRecordHex)
        obj.put("rssi", interpolatedRssi)
        val isSelectedBt = explicitBtAddress != null && explicitBtAddress.equals(address, ignoreCase = true)
        if (isSelectedBt) {
            obj.put("isConnected", true)
            btList.add(0, obj)
        } else {
            btList.add(obj)
        }
    }
    btList.forEach { btArr.put(it) }

    return Triple(wifiArr.toString(), cellArr.toString(), btArr.toString())
}

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

internal fun MainViewModel.importEnvironmentData(uri: android.net.Uri, onComplete: () -> Unit) {
    parseImportPackage(uri) { pkg ->
        if (pkg == null) onComplete()
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

    val pkg = try {
        json.decodeFromString<io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage>(jsonStr)
    } catch (e: Exception) {
        null
    }
    val pkgHasContent = pkg != null && (
            pkg.locations.isNotEmpty() || pkg.collectionRoutes.isNotEmpty() || pkg.savedLocations.isNotEmpty() ||
                    pkg.savedRoutes.isNotEmpty() ||
                    pkg.settings != null || pkg.apiKeys != null
            )
    if (pkgHasContent) return pkg

    try {
        val legacyLocations =
            json.decodeFromString<List<io.github.amahteru.locationspoofer.data.db.CompleteLocation>>(jsonStr)
        if (legacyLocations.isNotEmpty()) {
            return io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage(locations = legacyLocations)
        }
    } catch (e: Exception) {
        try {
            val legacySaved = json.decodeFromString<List<SavedLocation>>(jsonStr)
            if (legacySaved.isNotEmpty()) {
                return io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage(savedLocations = legacySaved)
            }
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }

    return pkg
}

internal fun MainViewModel.applyImportPackage(
    pkg: io.github.amahteru.locationspoofer.data.db.LocationSpooferDataPackage,
    selection: ImportExportSelection,
    onComplete: () -> Unit
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
                    if (currentSaved.none { it.name == loc.name && it.lat == loc.lat && it.lng == loc.lng }) {
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
                onComplete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            launch(Dispatchers.Main) { onComplete() }
        }
    }
}
