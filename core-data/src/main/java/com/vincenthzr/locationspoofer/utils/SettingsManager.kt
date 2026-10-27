package com.vincenthzr.locationspoofer.utils

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import com.vincenthzr.locationspoofer.data.BuildConfig
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.model.SavedLocation
import com.vincenthzr.locationspoofer.data.model.SavedRoute
import org.json.JSONArray
import org.json.JSONObject

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean("mock_switches_migrated_v3", false)) {
            prefs.edit {
                putBoolean("mock_wifi", true)
                putBoolean("mock_cell", true)
                putBoolean("mock_bluetooth", true)
                putBoolean("mock_switches_migrated_v3", true)
            }
        }
    }

    var isDarkMode: Boolean
        get() = prefs.getBoolean("is_dark_mode", true)
        set(value) = prefs.edit { putBoolean("is_dark_mode", value) }

    var language: String
        get() = LocaleUtils.normalizeLanguage(prefs.getString("language", "") ?: "")
        set(value) = prefs.edit { putString("language", value) }

    var isLanguageSet: Boolean
        get() = prefs.getBoolean("is_language_set", false)
        set(value) = prefs.edit { putBoolean("is_language_set", value) }

    var amapApiKey: String
        get() = prefs.getString("amap_api_key", "") ?: ""
        set(value) = prefs.edit { putString("amap_api_key", value) }

    var baiduApiKey: String
        get() = prefs.getString("baidu_api_key", "") ?: ""
        set(value) = prefs.edit { putString("baidu_api_key", value) }

    var googleApiKey: String
        get() = prefs.getString("google_api_key", "") ?: ""
        set(value) = prefs.edit { putString("google_api_key", value) }

    var wigleApiToken: String
        get() = prefs.getString("wigle_api_token", "") ?: ""
        set(value) = prefs.edit { putString("wigle_api_token", value) }

    var opencellidApiToken: String
        get() = prefs.getString("opencellid_api_token", "") ?: ""
        set(value) = prefs.edit { putString("opencellid_api_token", value) }

    var mapType: String
        get() = prefs.getString("map_type", "NORMAL") ?: "NORMAL"
        set(value) = prefs.edit { putString("map_type", value) }

    var mapEngine: String
        get() = prefs.getString("map_engine", "AUTO") ?: "AUTO"
        set(value) = prefs.edit { putString("map_engine", value) }

    var rootSolution: String
        get() = prefs.getString("root_solution", "AUTO") ?: "AUTO"
        set(value) = prefs.edit { putString("root_solution", value) }

    var ignoredVersion: String
        get() = prefs.getString("ignored_version", "") ?: ""
        set(value) = prefs.edit { putString("ignored_version", value) }

    var checkBetaUpdates: Boolean
        get() = prefs.getBoolean("check_beta_updates", false)
        set(value) = prefs.edit { putBoolean("check_beta_updates", value) }

    var isSpoofingActive: Boolean
        get() = prefs.getBoolean("is_spoofing_active", false)
        set(value) = prefs.edit { putBoolean("is_spoofing_active", value) }

    var lastSpoofedLat: String
        get() = prefs.getString("last_spoofed_lat", "0") ?: "0"
        set(value) = prefs.edit { putString("last_spoofed_lat", value) }

    var lastSpoofedLng: String
        get() = prefs.getString("last_spoofed_lng", "0") ?: "0"
        set(value) = prefs.edit { putString("last_spoofed_lng", value) }

    var mockWifi: Boolean
        get() = prefs.getBoolean("mock_wifi", true)
        set(value) = prefs.edit { putBoolean("mock_wifi", value) }

    var mockCell: Boolean
        get() = prefs.getBoolean("mock_cell", true)
        set(value) = prefs.edit { putBoolean("mock_cell", value) }

    var mockBluetooth: Boolean
        get() = prefs.getBoolean("mock_bluetooth", true)
        set(value) = prefs.edit { putBoolean("mock_bluetooth", value) }

    var enableJitter: Boolean
        get() = prefs.getBoolean("enable_jitter", true)
        set(value) = prefs.edit { putBoolean("enable_jitter", value) }

    var restartAppsOnSpoof: Boolean
        get() = prefs.getBoolean("restart_apps_on_spoof", true)
        set(value) = prefs.edit { putBoolean("restart_apps_on_spoof", value) }

    var altitude: String
        get() = prefs.getString("altitude", "0.0") ?: "0.0"
        set(value) = prefs.edit { putString("altitude", value) }

    var altitudeVariationM: Int
        get() = prefs.getInt("altitude_variation_m", AltitudeModel.DEFAULT_VARIATION_M)
        set(value) = prefs.edit { putInt("altitude_variation_m", value) }

    var realismLevel: Int
        get() = prefs.getInt("realism_level", MotionRealism.DEFAULT_LEVEL_ID)
        set(value) = prefs.edit { putInt("realism_level", value) }

    var speedFluctuationPct: Int
        get() = prefs.getInt("speed_fluctuation_pct", MotionRealism.DEFAULT_SPEED_FLUCTUATION_PCT)
        set(value) = prefs.edit { putInt("speed_fluctuation_pct", value) }

    var stepSimulationEnabled: Boolean
        get() = prefs.getBoolean("enable_step_simulation", true)
        set(value) = prefs.edit { putBoolean("enable_step_simulation", value) }

    var keepLastMapPosition: Boolean
        get() = prefs.getBoolean("keep_last_map_position", true)
        set(value) = prefs.edit { putBoolean("keep_last_map_position", value) }

    var lastMapLat: String
        get() = prefs.getString("last_map_lat", "") ?: ""
        set(value) = prefs.edit { putString("last_map_lat", value) }

    var lastMapLng: String
        get() = prefs.getString("last_map_lng", "") ?: ""
        set(value) = prefs.edit { putString("last_map_lng", value) }

    var satelliteCount: String
        get() = prefs.getString("satellite_count", "10") ?: "10"
        set(value) = prefs.edit { putString("satellite_count", value) }

    fun getSavedLocations(): List<SavedLocation> {
        val jsonString = prefs.getString("saved_locations", "[]") ?: "[]"
        val list = mutableListOf<SavedLocation>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SavedLocation(
                        name = obj.optString("name", ""),
                        lat = obj.optDouble("lat", 0.0),
                        lng = obj.optDouble("lng", 0.0),
                        wifiJson = obj.optString("wifiJson", "[]"),
                        cellJson = obj.optString("cellJson", "[]"),
                        bluetoothJson = obj.optString("bluetoothJson", "[]"),
                        sourceLocationId = if (obj.has("sourceLocationId")) obj.optLong("sourceLocationId") else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun isSameSavedLocation(a: SavedLocation, b: SavedLocation): Boolean {
        if (a.sourceLocationId != null && b.sourceLocationId != null) {
            return a.sourceLocationId == b.sourceLocationId
        }
        return a.name == b.name && a.lat == b.lat && a.lng == b.lng
    }

    fun addSavedLocation(location: SavedLocation) {
        val list = getSavedLocations().toMutableList()
        list.removeAll { isSameSavedLocation(it, location) }
        list.add(location)
        saveLocationList(list)
    }

    fun removeSavedLocation(location: SavedLocation) {
        val list = getSavedLocations().toMutableList()
        list.removeAll { isSameSavedLocation(it, location) }
        saveLocationList(list)
    }

    fun saveLocationList(list: List<SavedLocation>) {
        val jsonArray = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("lat", it.lat)
            obj.put("lng", it.lng)
            obj.put("wifiJson", it.wifiJson)
            obj.put("cellJson", it.cellJson)
            obj.put("bluetoothJson", it.bluetoothJson)
            if (it.sourceLocationId != null) {
                obj.put("sourceLocationId", it.sourceLocationId)
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString("saved_locations", jsonArray.toString()) }
    }

    fun getSavedRoutes(): List<SavedRoute> {
        val jsonString = prefs.getString("saved_routes", "[]") ?: "[]"
        val list = mutableListOf<SavedRoute>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pointsArray = obj.getJSONArray("points")
                val points = (0 until pointsArray.length()).map { j ->
                    val p = pointsArray.getJSONObject(j)
                    RoutePoint(p.getDouble("lat"), p.getDouble("lng"))
                }
                list.add(SavedRoute(obj.getString("name"), points))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addSavedRoute(route: SavedRoute) {
        val list = getSavedRoutes().toMutableList()
        list.add(route)
        saveRouteList(list)
    }

    fun removeSavedRoute(route: SavedRoute) {
        val list = getSavedRoutes().toMutableList()
        list.removeAll { it.name == route.name }
        saveRouteList(list)
    }

    private fun saveRouteList(list: List<SavedRoute>) {
        val jsonArray = JSONArray()
        list.forEach { route ->
            val obj = JSONObject()
            obj.put("name", route.name)
            val pointsArray = JSONArray()
            route.points.forEach { p ->
                val pObj = JSONObject()
                pObj.put("lat", p.lat)
                pObj.put("lng", p.lng)
                pointsArray.put(pObj)
            }
            obj.put("points", pointsArray)
            jsonArray.put(obj)
        }
        prefs.edit { putString("saved_routes", jsonArray.toString()) }
    }
}
