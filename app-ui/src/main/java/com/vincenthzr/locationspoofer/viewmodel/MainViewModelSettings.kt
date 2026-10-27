package com.vincenthzr.locationspoofer.viewmodel

import com.vincenthzr.locationspoofer.ui.BuildConfig
import java.util.Locale
import androidx.lifecycle.viewModelScope
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.utils.AltitudeModel
import com.vincenthzr.locationspoofer.data.model.RoutePlanStage
import com.vincenthzr.locationspoofer.data.model.AppMapType
import com.vincenthzr.locationspoofer.data.model.MapEngine
import com.vincenthzr.locationspoofer.data.model.RootSolution
import com.vincenthzr.locationspoofer.data.model.SearchMode
import com.vincenthzr.locationspoofer.ui.screen.AppPoiItem
import com.vincenthzr.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.vincenthzr.locationspoofer.utils.XposedModuleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel.ClusterData

@OptIn(kotlinx.coroutines.FlowPreview::class)
internal fun MainViewModel.initialize() {
    viewModelScope.launch {
        var lastShown = 0L
        locationRepository.configWriteFailures.collect {
            val now = System.currentTimeMillis()
            if (now - lastShown < 10_000L) return@collect
            lastShown = now
            android.widget.Toast.makeText(context, R.string.config_write_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    viewModelScope.launch(Dispatchers.IO) {
        mergeLegacyRecords()
        val root = locationRepository.recoverAfterBoot(context)
        motionController.syncFromSpoofingStateIfIdle()

        val restoredPosition = when {
            settingsRepository.isSpoofingActive -> settingsRepository.lastSpoofedLat to settingsRepository.lastSpoofedLng
            settingsRepository.keepLastMapPosition -> settingsRepository.getLastMapPosition()
            else -> null
        }
        restoredSpoofPoint = if (settingsRepository.isSpoofingActive) {
            val lat = restoredPosition?.first?.toDoubleOrNull()
            val lng = restoredPosition?.second?.toDoubleOrNull()
            if (lat != null && lng != null) com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng(lat, lng) else null
        } else null

        _uiState.update {
            it.copy(
                isInitializing = false,
                hasRootAccess = root,
                isSpoofingActive = settingsRepository.isSpoofingActive,
                latitudeInput = restoredPosition?.first ?: it.latitudeInput,
                longitudeInput = restoredPosition?.second ?: it.longitudeInput,
                routePlanStage = RoutePlanStage.IDLE,
                amapApiKey = settingsRepository.getAmapApiKey(),
                baiduApiKey = settingsRepository.getBaiduApiKey(),
                googleApiKey = settingsRepository.getGoogleApiKey(),
                appSha1 = getAppSignatureSHA1(),
                checkBetaUpdates = settingsRepository.checkBetaUpdates
            )
        }
        if (restoredPosition == null) {
            fetchCurrentLocation(context)
        }
        withContext(Dispatchers.Main) { evaluateMockCapabilities() }
    }

    observeMotionController()

    viewModelScope.launch {
        combine(environmentLocations, collectionRoutes) { locations, routes -> locations to routes }.collect { (locations, routes) ->
            _uiState.update { it.copy(environmentRecordCount = locations.count { point -> point.location.collectionRouteId == null } + routes.size) }
            evaluateMockCapabilities()
        }
    }

    viewModelScope.launch {
        _uiState
            .map { it.latitudeInput to it.longitudeInput }
            .distinctUntilChanged()
            .sample(2_000)
            .collect { (lat, lng) ->
                if (lat.toDoubleOrNull() != null && lng.toDoubleOrNull() != null) {
                    settingsRepository.setLastMapPosition(lat, lng)
                }
            }
    }

    viewModelScope.launch {
        XposedModuleStatus.isModuleActive.collect { active ->
            _uiState.update {
                it.copy(
                    isLSPosedActive = active,
                    hookedApps = if (active) lsposedManager.getHookedApps(context) else emptyList()
                )
            }
        }
    }

    viewModelScope.launch {
        locationRepository.getSavedRoutes().collect { entities ->
            val routes = entities.map { entity ->
                val points = mutableListOf<RoutePoint>()
                try {
                    val arr = org.json.JSONArray(entity.pointsJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        points.add(RoutePoint(obj.getDouble("lat"), obj.getDouble("lng")))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                com.vincenthzr.locationspoofer.data.model.SavedRoute(entity.name, points)
            }
            _uiState.update { it.copy(savedRoutes = routes) }
        }
    }
}

private suspend fun MainViewModel.mergeLegacyRecords() {
    val allComplete = environmentDao.getAllCompleteLocations().filter { it.location.collectionRouteId == null }
    if (allComplete.isEmpty()) return

    val grouped = allComplete.groupBy {
        Pair(
            String.format(java.util.Locale.US, "%.4f", it.location.lat),
            String.format(java.util.Locale.US, "%.4f", it.location.lng)
        )
    }.filter { it.value.size > 1 }

    for ((_, group) in grouped) {
        val primary = group.maxByOrNull { it.location.timestamp } ?: continue
        val others = group.filter { it.location.id != primary.location.id }

        for (other in others) {
            other.connectedWifi?.let {
                environmentDao.insertConnectedWifi(it.copy(locationId = primary.location.id))
            }
            other.wifis.forEach { lw ->
                environmentDao.insertLocationWifi(lw.locationWifi.copy(locationId = primary.location.id))
            }
            other.bluetooths.forEach { lb ->
                environmentDao.insertLocationBluetooth(lb.locationBluetooth.copy(locationId = primary.location.id))
            }
            other.cells.forEach { lc ->
                environmentDao.insertLocationCell(lc.locationCell.copy(locationId = primary.location.id))
            }

            environmentDao.deleteLocation(other.location.id)
        }
    }
}

internal fun MainViewModel.setMapType(type: AppMapType) {
    settingsRepository.setMapType(type.name)
    _uiState.update { it.copy(mapType = type) }
}

internal fun MainViewModel.setMapEngine(engine: MapEngine) {
    settingsRepository.setMapEngine(engine.name)
    _uiState.update { it.copy(mapEngine = engine) }
}

internal fun MainViewModel.setRootSolution(solution: RootSolution) {
    settingsRepository.setRootSolution(solution.name)
    _uiState.update { it.copy(rootSolution = solution) }
}

internal fun MainViewModel.testRootSetup() {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isTestingRootSetup = true) }
        val result = locationRepository.testRootSetup()
        _uiState.update { it.copy(isTestingRootSetup = false, rootSetupTestResult = result) }
    }
}

internal fun MainViewModel.dismissRootSetupTestResult() {
    _uiState.update { it.copy(rootSetupTestResult = null) }
}

internal fun MainViewModel.requestRestartHookedApps() {
    _uiState.update { it.copy(hookedAppsToRestart = lsposedManager.getHookedApps(context)) }
}

internal fun MainViewModel.dismissRestartHookedAppsDialog() {
    _uiState.update { it.copy(hookedAppsToRestart = null) }
}

internal fun MainViewModel.confirmRestartHookedApps(onDone: (Int) -> Unit = {}) {
    val apps = _uiState.value.hookedAppsToRestart ?: return
    _uiState.update { it.copy(isRestartingHookedApps = true, hookedAppsToRestart = null) }
    viewModelScope.launch(Dispatchers.IO) {
        locationRepository.checkRootAccess()
        locationRepository.forceStopApps(apps.map { it.packageName })
        _uiState.update { it.copy(isRestartingHookedApps = false) }
        withContext(Dispatchers.Main) { onDone(apps.size) }
    }
}

internal fun MainViewModel.setSearchMode(mode: SearchMode) {
    _uiState.update { it.copy(searchMode = mode) }
}

internal suspend fun MainViewModel.performLocalSearch(): List<AppPoiItem> {
    val allRecords = environmentDao.getAllCompleteLocations()
    if (allRecords.isEmpty()) {
        return emptyList()
    }

    val clusters = mutableListOf<ClusterData>()

    for (record in allRecords) {
        val loc = record.location
        val hasW = record.wifis.isNotEmpty()
        val hasB = record.bluetooths.isNotEmpty()
        val hasC = record.cells.isNotEmpty()

        var foundCluster = false
        for (i in clusters.indices) {
            val cluster = clusters[i]
            val dLat = Math.toRadians(cluster.center.lat - loc.lat)
            val dLng = Math.toRadians(cluster.center.lng - loc.lng)
            val a = kotlin.math.sin(dLat / 2).let { it * it } +
                    kotlin.math.cos(Math.toRadians(loc.lat)) *
                    kotlin.math.cos(Math.toRadians(cluster.center.lat)) *
                    kotlin.math.sin(dLng / 2).let { it * it }
            val distance =
                2 * 6378137.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

            if (distance <= 150.0) {
                cluster.count += 1
                cluster.hasWifi = cluster.hasWifi || hasW
                cluster.hasBluetooth = cluster.hasBluetooth || hasB
                cluster.hasCell = cluster.hasCell || hasC
                foundCluster = true
                break
            }
        }
        if (!foundCluster) {
            clusters.add(ClusterData(loc, 1, hasW, hasB, hasC))
        }
    }

    clusters.sortByDescending { it.count }

    return clusters.map { cluster ->
        val tags = mutableListOf<String>()
        if (cluster.hasWifi) tags.add("Wi-Fi")
        if (cluster.hasBluetooth) tags.add(context.getString(R.string.tag_bluetooth))
        if (cluster.hasCell) tags.add(context.getString(R.string.tag_cell))

        val tagStr = if (tags.isNotEmpty()) " [${tags.joinToString(", ")}]" else ""

        val baseTitle = when {
            cluster.center.remark.isNotEmpty() -> cluster.center.remark
            cluster.center.placeName.isNotEmpty() -> cluster.center.placeName
            else -> context.getString(R.string.local_collected_hotspot)
        }

        val recordsSnippet = context.getString(R.string.contains_records_format, cluster.count)
        com.vincenthzr.locationspoofer.ui.screen.AppPoiItem(
            title = "$baseTitle$tagStr",
            snippet = "$recordsSnippet (${
                String.format(
                    java.util.Locale.US, "%.4f",
                    cluster.center.lat
                )
            }, ${String.format(java.util.Locale.US, "%.4f", cluster.center.lng)})",
            lat = cluster.center.lat,
            lng = cluster.center.lng
        )
    }
}

internal fun MainViewModel.selectLanguage(languageCode: String) {
    settingsRepository.setLanguage(languageCode)
    settingsRepository.setLanguageSet(true)
    _uiState.update { it.copy(isLanguageSet = true, currentLanguage = languageCode) }
}

fun MainViewModel.getSavedLanguage(): String = settingsRepository.getLanguage()

internal fun MainViewModel.setAltitude(altitude: String) {
    settingsRepository.altitude = altitude
    _uiState.update { it.copy(altitudeInput = altitude) }
}

internal fun MainViewModel.setAltitudeVariation(meters: Int) {
    val value = meters.coerceIn(0, AltitudeModel.MAX_VARIATION_M)
    settingsRepository.altitudeVariationM = value
    _uiState.update { it.copy(altitudeVariationM = value) }
}

internal fun MainViewModel.setSatelliteCount(count: String) {
    settingsRepository.satelliteCount = count
    _uiState.update { it.copy(satelliteCountInput = count) }
}

internal fun MainViewModel.isDomesticEnvironment(): Boolean = true

internal fun MainViewModel.normalizeCellArrayForStorage(cellsArray: org.json.JSONArray): org.json.JSONArray {
    val formattedCells = org.json.JSONArray()
    for (i in 0 until cellsArray.length()) {
        val cell = cellsArray.optJSONObject(i) ?: continue
        val area = cellArea(cell)
        val identity = cellIdentity(cell)
        if (area <= 0 || identity <= 0) {
            continue
        }

        val type = normalizeCellType(cell.optString("type", cell.optString("radio", "LTE")))
        val cellObj = org.json.JSONObject().apply {
            put("type", type)
            put("radio", cell.optString("radio", type))
            put("mcc", positiveCellInt(cell, "mcc", default = 460))
            put("mnc", positiveCellInt(cell, "mnc", "net", default = 0))
            put("tac", area)
            put("lac", area)
            put("ci", identity)
            put("cid", identity)
            put("cellid", identity)
            put(
                "pci",
                positiveCellInt(cell, "pci", default = (identity % 504).coerceIn(0, 503))
            )
            put("dbm", cellSignalDbm(cell, i))
            put("isRegistered", cell.optBoolean("isRegistered", i == 0))
        }
        formattedCells.put(cellObj)
    }
    return formattedCells
}

internal fun MainViewModel.normalizeCellType(rawType: String): String {
    return when (rawType.uppercase(java.util.Locale.US)) {
        "GSM" -> "GSM"
        "UMTS", "WCDMA" -> "WCDMA"
        "NR", "NR5G", "5G" -> "NR"
        else -> "LTE"
    }
}

internal fun MainViewModel.cellArea(cell: org.json.JSONObject): Int =
    positiveCellInt(cell, "tac", "lac", "area", default = 0)

internal fun MainViewModel.cellIdentity(cell: org.json.JSONObject): Int =
    positiveCellInt(cell, "ci", "cid", "cellid", "cell", default = 0)

internal fun MainViewModel.positiveCellInt(cell: org.json.JSONObject, vararg keys: String, default: Int): Int {
    for (key in keys) {
        if (!cell.has(key) || cell.isNull(key)) continue
        val value = cell.optInt(key, Int.MIN_VALUE)
        if (value > 0) return value
        val parsed = cell.optString(key).toIntOrNull()
        if (parsed != null && parsed > 0) return parsed
    }
    return default
}

internal fun MainViewModel.cellSignalDbm(cell: org.json.JSONObject, index: Int): Int {
    val direct = cell.optInt("dbm", Int.MIN_VALUE)
    if (direct in -140..-40) return direct
    val average = cell.optInt("averageSignalStrength", Int.MIN_VALUE)
    if (average in -140..-40) return average
    val signal = cell.optInt("signal", Int.MIN_VALUE)
    if (signal in -140..-40) return signal
    return (-70 - index * 3).coerceAtLeast(-110)
}

@android.annotation.SuppressLint("MissingPermission")

internal fun MainViewModel.setAmapApiKey(key: String) {
    settingsRepository.setAmapApiKey(key)
    _uiState.update { it.copy(amapApiKey = key) }
}

internal fun MainViewModel.setBaiduApiKey(key: String) {
    settingsRepository.setBaiduApiKey(key)
    _uiState.update { it.copy(baiduApiKey = key) }
}

internal fun MainViewModel.setGoogleApiKey(key: String) {
    settingsRepository.setGoogleApiKey(key)
    _uiState.update { it.copy(googleApiKey = key) }
}

internal fun MainViewModel.setWigleApiToken(token: String) {
    settingsRepository.setWigleApiToken(token)
    _uiState.update { it.copy(wigleToken = token) }
}

internal fun MainViewModel.setOpencellidApiToken(token: String) {
    settingsRepository.setOpencellidApiToken(token)
    _uiState.update { it.copy(opencellidToken = token) }
}

@Suppress("DEPRECATION")

private fun MainViewModel.getAppSignatureSHA1(): String {
    try {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_SIGNATURES
        )
        val signatures = info.signatures ?: return "Unknown"
        if (signatures.isEmpty()) return "Unknown"
        val cert = signatures[0].toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA1")
        val publicKey = md.digest(cert)
        val hexString = StringBuilder()
        for (b in publicKey) {
            val appendString = Integer.toHexString(0xFF and b.toInt())
            if (appendString.length == 1) hexString.append("0")
            hexString.append(appendString)
            hexString.append(":")
        }
        return hexString.toString().dropLast(1).uppercase()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "Unknown"
}

internal fun MainViewModel.getIgnoredVersion(): String = settingsRepository.getIgnoredVersion()

internal fun MainViewModel.setIgnoredVersion(version: String) {
    settingsRepository.setIgnoredVersion(version)
}

internal fun MainViewModel.setCheckBetaUpdates(enabled: Boolean) {
    settingsRepository.checkBetaUpdates = enabled
    _uiState.update { it.copy(checkBetaUpdates = enabled) }
}

internal fun MainViewModel.handleSpoofingIntent(intent: SpoofingIntent) {
    when (intent) {
        is SpoofingIntent.SetSaveDialogVisible -> _spoofingUiState.update {
            it.copy(
                showSaveDialog = intent.visible
            )
        }

        is SpoofingIntent.SetSavedLocationsVisible -> _spoofingUiState.update {
            it.copy(
                showSavedLocationsDialog = intent.visible
            )
        }

        is SpoofingIntent.SetMapTypeDialogVisible -> _spoofingUiState.update {
            it.copy(
                showMapTypeDialog = intent.visible
            )
        }

        is SpoofingIntent.SetCustomCoordDialogVisible -> _spoofingUiState.update {
            it.copy(
                showCustomCoordDialog = intent.visible
            )
        }

        is SpoofingIntent.SetStartSpoofingDialogVisible -> {
            if (intent.visible && !_spoofingUiState.value.showStartSpoofingDialog) {
                _uiState.update { state ->
                    state.copy(
                        mockWifi = state.canMockWifi || state.mockWifi,
                        mockCell = state.canMockCell || state.mockCell,
                        mockBluetooth = state.canMockBluetooth || state.mockBluetooth
                    )
                }
                val state = _uiState.value
                settingsRepository.mockWifi = state.mockWifi
                settingsRepository.mockCell = state.mockCell
                settingsRepository.mockBluetooth = state.mockBluetooth
            }
            _spoofingUiState.update { it.copy(showStartSpoofingDialog = intent.visible) }
        }


        is SpoofingIntent.SetSheetExpanded -> _spoofingUiState.update {
            it.copy(
                isSheetExpanded = intent.expanded
            )
        }

        is SpoofingIntent.SetSearchActive -> _spoofingUiState.update {
            it.copy(
                isSearchActive = intent.active
            )
        }

        SpoofingIntent.HideSearchResults -> _spoofingUiState.update {
            it.copy(showSearchResults = false)
        }

        is SpoofingIntent.UpdateSearchQuery -> _spoofingUiState.update {
            it.copy(
                searchQuery = intent.query
            )
        }

        is SpoofingIntent.PerformSearch -> {
        }

        is SpoofingIntent.ClearSearchResults -> _spoofingUiState.update {
            it.copy(
                searchResults = emptyList(),
                showSearchResults = false
            )
        }

        is SpoofingIntent.SetSearchResults -> _spoofingUiState.update {
            val query = intent.query.ifBlank { it.searchQuery }
            it.copy(
                searchResults = intent.results,
                showSearchResults = intent.show,
                cachedSearchQuery = if (intent.results.isNotEmpty()) query else it.cachedSearchQuery,
                cachedSearchAt = if (intent.results.isNotEmpty()) System.currentTimeMillis() else it.cachedSearchAt
            )
        }

        is SpoofingIntent.ConfirmMapPoint -> confirmMapPoint(
            intent.lat,
            intent.lng
        )

        is SpoofingIntent.MapPointMoved -> {
            val now = System.currentTimeMillis()
            if (now - lastMapMoveTime > 500) {
                lastMapMoveTime = now
                confirmMapPoint(intent.lat, intent.lng, isDragging = true)
            } else {
                mapMoveJob?.cancel()
                mapMoveJob = viewModelScope.launch {
                    delay(500 - (now - lastMapMoveTime))
                    lastMapMoveTime = System.currentTimeMillis()
                    confirmMapPoint(intent.lat, intent.lng, isDragging = true)
                }
            }
        }

        is SpoofingIntent.RequestCurrentLocation -> {}
    }
}
