package io.github.amahteru.locationspoofer.utils

import io.github.amahteru.locationspoofer.data.model.isAt
import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import io.github.amahteru.locationspoofer.data.model.SavedLocation
import org.json.JSONArray
import org.json.JSONObject

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var language: String
        get() = LocaleUtils.normalizeLanguage(prefs.getString("language", "") ?: "")
        set(value) = prefs.edit { putString("language", value) }

    var isLanguageSet: Boolean
        get() = prefs.getBoolean("is_language_set", false)
        set(value) = prefs.edit { putBoolean("is_language_set", value) }

    var isPermissionSetupDone: Boolean
        get() = prefs.getBoolean("is_permission_setup_done", false)
        set(value) = prefs.edit { putBoolean("is_permission_setup_done", value) }

    var blockedPermissions: Set<String>
        get() = prefs.getStringSet("blocked_permissions", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet("blocked_permissions", value.toSet()) }

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

    var vendorOverride: String
        get() = prefs.getString("vendor_override", "auto") ?: "auto"
        set(value) = prefs.edit { putString("vendor_override", value) }

    var debugDumpSystemServices: Boolean
        get() = prefs.getBoolean("debug_dump_system_services", false)
        set(value) = prefs.edit { putBoolean("debug_dump_system_services", value) }

    var ignoredVersion: String
        get() = prefs.getString("ignored_version", "") ?: ""
        set(value) = prefs.edit { putString("ignored_version", value) }

    var githubToken: String
        get() = prefs.getString("github_token", "") ?: ""
        set(value) = prefs.edit { putString("github_token", value) }

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
        get() = prefs.getBoolean("restart_apps_on_spoof", false)
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
                        lng = obj.optDouble("lng", 0.0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addSavedLocation(location: SavedLocation) {
        val list = getSavedLocations().toMutableList()
        list.removeAll { it.isAt(location.lat, location.lng) }
        list.add(location)
        saveLocationList(list)
    }

    fun removeSavedLocation(location: SavedLocation) {
        val list = getSavedLocations().toMutableList()
        list.removeAll { it.isAt(location.lat, location.lng) }
        saveLocationList(list)
    }

    fun saveLocationList(list: List<SavedLocation>) {
        val jsonArray = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("lat", it.lat)
            obj.put("lng", it.lng)
            jsonArray.put(obj)
        }
        prefs.edit { putString("saved_locations", jsonArray.toString()) }
    }

    fun getSystemHookPackages(): Set<String> {
        val jsonString = prefs.getString("system_hook_packages", "[]") ?: "[]"
        val set = mutableSetOf<String>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return set
    }

    fun setSystemHookPackages(packages: Set<String>) {
        val jsonArray = JSONArray()
        packages.forEach { jsonArray.put(it) }
        prefs.edit { putString("system_hook_packages", jsonArray.toString()) }
    }

    var isSystemHookGlobalMode: Boolean
        get() = prefs.getBoolean("is_system_hook_global_mode", true)
        set(value) = prefs.edit { putBoolean("is_system_hook_global_mode", value) }
    var forceLocationEnabled: Boolean
        get() = prefs.getBoolean("force_location_enabled", false)
        set(value) = prefs.edit { putBoolean("force_location_enabled", value) }
}
