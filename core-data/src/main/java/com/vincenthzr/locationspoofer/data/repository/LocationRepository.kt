package com.vincenthzr.locationspoofer.data.repository

import android.content.Context
import com.vincenthzr.locationspoofer.data.db.SavedRouteDao
import com.vincenthzr.locationspoofer.data.db.SavedRouteEntity
import com.vincenthzr.locationspoofer.data.model.RootSetupTestResult
import com.vincenthzr.locationspoofer.data.model.RootSolution
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.state.SpoofingState
import com.vincenthzr.locationspoofer.utils.ConfigManager
import com.vincenthzr.locationspoofer.utils.LSPosedManager
import com.vincenthzr.locationspoofer.utils.RootManager
import com.vincenthzr.locationspoofer.utils.SettingsManager
import org.json.JSONArray
import org.json.JSONObject

class LocationRepository(
    private val configManager: ConfigManager,
    private val rootManager: RootManager,
    private val lsposedManager: LSPosedManager,
    private val settingsManager: SettingsManager,
    private val savedRouteDao: SavedRouteDao,
    private val spoofingServiceController: SpoofingServiceController
) {
    private fun currentRootSolution(): RootSolution =
        try {
            RootSolution.valueOf(settingsManager.rootSolution)
        } catch (e: Exception) {
            RootSolution.AUTO
        }

    suspend fun checkRootAccess(): Boolean = rootManager.checkRootAccess(currentRootSolution())

    suspend fun testRootSetup(): RootSetupTestResult = rootManager.testRootSetup(currentRootSolution())

    suspend fun forceStopApps(packages: List<String>) = rootManager.forceStopApps(packages)

    suspend fun recoverAfterBoot(context: Context): Boolean {
        val hasRoot = rootManager.checkRootAccess(currentRootSolution())

        val desired = configManager.desiredConfig()
        val active = desired?.optBoolean("active", false) ?: settingsManager.isSpoofingActive
        if (active && desired != null) {
            restoreSpoofingState(desired)
            spoofingServiceController.startForeground(context, SpoofingState.latitude, SpoofingState.longitude)
        } else if (active) {
            val lastLat = settingsManager.lastSpoofedLat.toDoubleOrNull() ?: 0.0
            val lastLng = settingsManager.lastSpoofedLng.toDoubleOrNull() ?: 0.0
            if (lastLat != 0.0 && lastLng != 0.0) {
                startSpoofing(
                    context, lastLat, lastLng,
                    "STILL", 0f, System.currentTimeMillis(),
                    emptyList(), false,
                    mockWifi = settingsManager.mockWifi,
                    mockCell = settingsManager.mockCell,
                    mockBluetooth = settingsManager.mockBluetooth,
                    enableJitter = settingsManager.enableJitter
                )
            }
        } else if (spoofingServiceController.isRunning) {
            stopSpoofing(context)
        }

        return hasRoot
    }

    private fun restoreSpoofingState(config: JSONObject) {
        SpoofingState.isActive = true
        SpoofingState.latitude = config.optDouble("lat", 0.0)
        SpoofingState.longitude = config.optDouble("lng", 0.0)
        SpoofingState.simMode = config.optString("sim_mode", "STILL")
        SpoofingState.simBearing = config.optDouble("sim_bearing", 0.0).toFloat()
        SpoofingState.startTimestamp = config.optLong("start_timestamp", 0L)
        SpoofingState.routeJson = config.optJSONArray("route_points")?.toString() ?: "[]"
        SpoofingState.isRouteMode = config.optBoolean("is_route_mode", false)
        SpoofingState.routeDistanceOffset = config.optDouble("route_distance_offset", 0.0)
        SpoofingState.wifiJson = config.optJSONObject("wifi_json")?.toString() ?: "[]"
        SpoofingState.cellJson = config.optJSONArray("cell_json")?.toString() ?: "[]"
        SpoofingState.bluetoothJson = config.optJSONArray("bluetooth_json")?.toString() ?: "[]"
        SpoofingState.enableJitter = config.optBoolean("enable_jitter", true)
        SpoofingState.realismLevel = config.optInt("realism_level", -1)
        SpoofingState.speedFluctuationPct = config.optInt("speed_fluctuation_pct", -1)
    }

    fun isModuleActive(): Boolean = lsposedManager.isModuleActive()

    suspend fun startSpoofing(
        context: Context,
        lat: Double,
        lng: Double,
        simMode: String,
        simBearing: Float,
        startTime: Long,
        routePoints: List<RoutePoint>,
        isRouteMode: Boolean,
        wifiJson: String = "[]",
        cellJson: String = "[]",
        bluetoothJson: String = "[]",
        mockWifi: Boolean = true,
        mockCell: Boolean = true,
        mockBluetooth: Boolean = true,
        enableJitter: Boolean = true,
        speedMs: Double = 0.0,
        stopAtDestination: Boolean = false
    ) {
        SpoofingState.isActive = true
        SpoofingState.latitude = lat
        SpoofingState.longitude = lng
        SpoofingState.startTimestamp = startTime
        SpoofingState.realismLevel = settingsManager.realismLevel
        SpoofingState.speedFluctuationPct = settingsManager.speedFluctuationPct
        SpoofingState.routeDistanceOffset = 0.0
        SpoofingState.simMode = simMode
        SpoofingState.simBearing = simBearing
        SpoofingState.wifiJson = wifiJson
        SpoofingState.cellJson = cellJson
        SpoofingState.bluetoothJson = bluetoothJson
        SpoofingState.routeJson = routePointsToJson(routePoints)
        SpoofingState.isRouteMode = isRouteMode
        SpoofingState.enableJitter = enableJitter

        rootManager.clearMockLocationApp()

        val alt = settingsManager.altitude.toDoubleOrNull() ?: 0.0
        val satCount = settingsManager.satelliteCount.toIntOrNull() ?: 20
        configManager.saveConfig(
            lat,
            lng,
            true,
            simMode,
            simBearing,
            startTime,
            routePoints,
            isRouteMode,
            SpoofingState.wifiJson,
            SpoofingState.cellJson,
            SpoofingState.bluetoothJson,
            mockWifi,
            mockCell,
            mockBluetooth,
            enableJitter,
            alt,
            satCount,
            speedMs,
            stopAtDestination
        )

        spoofingServiceController.startForeground(context, lat, lng)
    }

    suspend fun stopSpoofing(context: Context) {
        SpoofingState.isActive = false
        SpoofingState.latitude = 0.0
        SpoofingState.longitude = 0.0
        SpoofingState.wifiJson = "[]"
        SpoofingState.cellJson = "[]"
        SpoofingState.bluetoothJson = "[]"
        SpoofingState.routeJson = "[]"
        SpoofingState.isRouteMode = false
        configManager.saveConfig(0.0, 0.0, false)

        spoofingServiceController.stop(context)
        rootManager.clearMockLocationApp()
    }

    val isFloatingJoystickShowing: Boolean get() = spoofingServiceController.isFloatingJoystickShowing

    fun setFloatingJoystickVisible(context: Context, visible: Boolean) =
        spoofingServiceController.setFloatingJoystickVisible(context, visible)

    suspend fun patchConfig(mutate: (JSONObject) -> Unit): Boolean = configManager.patchConfig(mutate)

    val configWriteFailures get() = configManager.writeFailures

    suspend fun updateEnvironment(wifiJson: String, cellJson: String, bluetoothJson: String) {
        SpoofingState.wifiJson = wifiJson
        SpoofingState.cellJson = cellJson
        SpoofingState.bluetoothJson = bluetoothJson
        configManager.patchConfig { configManager.putEnvironment(it, wifiJson, cellJson, bluetoothJson) }
    }

    suspend fun updateConfig(
        lat: Double,
        lng: Double,
        simMode: String,
        simBearing: Float,
        startTime: Long,
        routePoints: List<RoutePoint>,
        isRouteMode: Boolean,
        wifiJson: String = SpoofingState.wifiJson,
        cellJson: String = SpoofingState.cellJson,
        bluetoothJson: String = SpoofingState.bluetoothJson,
        mockWifi: Boolean = true,
        mockCell: Boolean = true,
        mockBluetooth: Boolean = true,
        enableJitter: Boolean = true,
        speedMs: Double = 0.0,
        stopAtDestination: Boolean = false
    ) {
        SpoofingState.latitude = lat
        SpoofingState.longitude = lng
        SpoofingState.startTimestamp = startTime
        SpoofingState.simMode = simMode
        SpoofingState.simBearing = simBearing
        SpoofingState.routeJson = routePointsToJson(routePoints)
        SpoofingState.isRouteMode = isRouteMode
        SpoofingState.wifiJson = wifiJson
        SpoofingState.cellJson = cellJson
        SpoofingState.bluetoothJson = bluetoothJson
        SpoofingState.enableJitter = enableJitter
        val alt = settingsManager.altitude.toDoubleOrNull() ?: 0.0
        val satCount = settingsManager.satelliteCount.toIntOrNull() ?: 20
        configManager.saveConfig(
            lat,
            lng,
            true,
            simMode,
            simBearing,
            startTime,
            routePoints,
            isRouteMode,
            SpoofingState.wifiJson,
            SpoofingState.cellJson,
            SpoofingState.bluetoothJson,
            mockWifi,
            mockCell,
            mockBluetooth,
            enableJitter,
            alt,
            satCount,
            speedMs,
            stopAtDestination
        )
    }

    private fun routePointsToJson(points: List<RoutePoint>): String {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(JSONObject().apply {
                put("lat", p.lat)
                put("lng", p.lng)
            })
        }
        return arr.toString()
    }

    fun getSavedRoutes(): kotlinx.coroutines.flow.Flow<List<SavedRouteEntity>> {
        return savedRouteDao.getAllSavedRoutes()
    }

    suspend fun getAllSavedRoutesList(): List<SavedRouteEntity> {
        return savedRouteDao.getAllSavedRoutesList()
    }

    suspend fun insertSavedRoute(name: String, points: List<RoutePoint>) {
        val pointsJson = routePointsToJson(points)
        savedRouteDao.insertSavedRoute(
            SavedRouteEntity(
                name = name,
                pointsJson = pointsJson
            )
        )
    }

    suspend fun insertSavedRouteEntity(route: SavedRouteEntity) {
        savedRouteDao.insertSavedRoute(route)
    }

    suspend fun deleteSavedRoute(route: SavedRouteEntity) {
        savedRouteDao.deleteSavedRoute(route)
    }
}
