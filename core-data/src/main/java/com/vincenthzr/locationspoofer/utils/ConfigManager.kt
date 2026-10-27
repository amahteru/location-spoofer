package com.vincenthzr.locationspoofer.utils

import androidx.core.content.edit
import android.content.Context
import android.location.Geocoder
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.state.SpoofingState
import com.vincenthzr.locationspoofer.utils.CoordinateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ConfigManager(private val context: Context, private val rootManager: RootManager) {
    private val settingsManager = SettingsManager(context)

    private var lastGeocodedLat = -999.0
    private var lastGeocodedLng = -999.0
    private var cachedProvince = ""
    private var cachedCity = ""
    private var cachedDistrict = ""
    private var cachedStreet = ""
    private var cachedStreetNum = ""
    private var cachedAddressText = ""
    private var cachedCountry = ""
    private var cachedPoiName = ""

    suspend fun saveConfig(
        lat: Double,
        lng: Double,
        active: Boolean,
        simMode: String = "STILL",
        simBearing: Float = 0f,
        startTimestamp: Long = System.currentTimeMillis(),
        routePoints: List<RoutePoint> = emptyList(),
        isRouteMode: Boolean = false,
        wifiJson: String = "[]",
        cellJson: String = "[]",
        bluetoothJson: String = "[]",
        mockWifi: Boolean = true,
        mockCell: Boolean = true,
        mockBluetooth: Boolean = true,
        enableJitter: Boolean = true,
        altitude: Double = 0.0,
        satelliteCount: Int = 20,
        speedMs: Double = 0.0,
        stopAtDestination: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val routeArray = JSONArray()
        routePoints.forEach { p ->
            val obj = JSONObject()
            obj.put("lat", p.lat)
            obj.put("lng", p.lng)
            routeArray.put(obj)
        }


        val json = JSONObject().apply {
            putPosition(this, lat, lng)
            put("active", active)
            put("sim_mode", simMode)
            put("sim_bearing", simBearing.toDouble())
            put("speed_m_s", speedMs)
            put("start_timestamp", startTimestamp)
            put("route_points", routeArray)
            put("is_route_mode", isRouteMode)
            put("stop_at_destination", stopAtDestination)
            putEnvironment(this, wifiJson, cellJson, bluetoothJson)
            put("mock_wifi", mockWifi)
            put("mock_cell", mockCell)
            put("mock_bluetooth", mockBluetooth)
            put("enable_jitter", enableJitter)
            put("altitude", altitude)
            put("satellite_count", satelliteCount)


            put("route_distance_offset", SpoofingState.routeDistanceOffset)
            putPersistentSettings(this)
        }
        write(json)
    }

    private val _writeFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val writeFailures: SharedFlow<Unit> = _writeFailures

    suspend fun patchConfig(mutate: (JSONObject) -> Unit): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val base = lastJson ?: return@withLock false
            val json = JSONObject(base.toString())
            mutate(json)
            putPersistentSettings(json)
            writeLocked(json)
        }
    }

    fun putPosition(json: JSONObject, lat: Double, lng: Double) {
        refreshGeocodeIfMoved(lat, lng)
        json.put("province", cachedProvince)
        json.put("city", cachedCity)
        json.put("district", cachedDistrict)
        json.put("street", cachedStreet)
        json.put("streetNum", cachedStreetNum)
        json.put("address", cachedAddressText)
        json.put("country", cachedCountry)
        json.put("poiName", cachedPoiName)

        val wgs = CoordinateUtils.gcj02ToWgs84(lat, lng)
        val bd = CoordinateUtils.gcj02ToBd09(lat, lng)
        json.put("wgs84_lat", wgs.lat)
        json.put("wgs84_lng", wgs.lng)
        json.put("bd09_lat", bd.lat)
        json.put("bd09_lng", bd.lng)
        json.put("lat", lat)
        json.put("lng", lng)
    }

    fun putEnvironment(json: JSONObject, wifiJson: String, cellJson: String, bluetoothJson: String) {
        val wifiObj = try {
            JSONObject(wifiJson)
        } catch (e: Exception) {
            JSONObject().apply {
                put("isConnected", false)
                put("connectedWifi", JSONObject.NULL)
                put("nearbyWifi", JSONArray())
            }
        }
        json.put("wifi_json", wifiObj)
        json.put("cell_json", JSONArray(cellJson))
        json.put("bluetooth_json", JSONArray(bluetoothJson))
    }

    private fun putPersistentSettings(json: JSONObject) {
        val systemHookPackagesArr = JSONArray()
        settingsManager.getSystemHookPackages().forEach { systemHookPackagesArr.put(it) }
        json.put("system_hook_packages", systemHookPackagesArr)
        json.put("system_hook_global_mode", settingsManager.isSystemHookGlobalMode)
        json.put("vendor_override", settingsManager.vendorOverride)
        json.put("force_location_enabled", settingsManager.forceLocationEnabled)
        json.put("debug_dump_system_services", settingsManager.debugDumpSystemServices)
        json.put("realism_level", SpoofingState.realismLevel.takeIf { it >= 0 } ?: settingsManager.realismLevel)
        json.put("speed_fluctuation_pct", SpoofingState.speedFluctuationPct.takeIf { it >= 0 } ?: settingsManager.speedFluctuationPct)
        json.put("altitude_variation_m", settingsManager.altitudeVariationM)
    }

    private fun refreshGeocodeIfMoved(lat: Double, lng: Double) {
        val dist = FloatArray(1)
        if (lastGeocodedLat != -999.0) {
            android.location.Location.distanceBetween(lastGeocodedLat, lastGeocodedLng, lat, lng, dist)
        }
        if (lastGeocodedLat != -999.0 && dist[0] <= 500f) return
        lastGeocodedLat = lat
        lastGeocodedLng = lng
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.CHINA)
            val wgs = CoordinateUtils.gcj02ToWgs84(lat, lng)

            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(wgs.lat, wgs.lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                cachedProvince = addr.adminArea ?: ""
                cachedCity = addr.locality ?: addr.subAdminArea ?: ""
                cachedDistrict = addr.subLocality ?: ""
                cachedStreet = addr.thoroughfare ?: ""
                cachedStreetNum = addr.subThoroughfare ?: ""
                cachedAddressText = addr.getAddressLine(0) ?: ""
                cachedCountry = addr.countryName ?: "中国"
                cachedPoiName = addr.featureName ?: ""
            }
        } catch (e: Exception) {
        }
    }

    private val writeMutex = Mutex()

    private val transportPrefs = context.getSharedPreferences("framework_config_state", Context.MODE_PRIVATE)
    private val publisher = FrameworkConfigPublisher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastJson: JSONObject? = transportPrefs.getString("desired_config", null)?.let {
        runCatching { JSONObject(it) }.getOrNull()
    }
    @Volatile private var publicationPending = lastJson != null

    fun desiredConfig(): JSONObject? = lastJson?.let { JSONObject(it.toString()) }
    private var nextCleanupAttemptAt = 0L

    init {
        scope.launch {
            XposedModuleStatus.service.collect { service ->
                if (service != null) writeMutex.withLock {
                    publisher.onReconnect()
                    lastJson?.let { publishLocked(it, service) }
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(3_000)
                if (publicationPending) writeMutex.withLock {
                    val service = XposedModuleStatus.mService
                    if (publicationPending && service != null) lastJson?.let {
                        publishLocked(it, service, notifyFailure = false)
                    }
                }
            }
        }
    }

    private suspend fun write(json: JSONObject): Boolean = writeMutex.withLock { writeLocked(json) }

    @android.annotation.SuppressLint("UseKtx")
    private fun writeLocked(json: JSONObject): Boolean {
        if (!transportPrefs.edit().putString("desired_config", json.toString()).commit()) {
            _writeFailures.tryEmit(Unit)
            return false
        }
        lastJson = json
        publicationPending = true
        val service = XposedModuleStatus.mService
        if (service == null) {
            android.util.Log.w("LocationSpoofer", "Framework disconnected; latest configuration queued")
            _writeFailures.tryEmit(Unit)
            return false
        }
        return publishLocked(json, service)
    }

    private fun publishLocked(
        json: JSONObject,
        service: io.github.libxposed.service.XposedService,
        notifyFailure: Boolean = true
    ): Boolean {
        return try {
            val prefs = service.getRemotePreferences(FrameworkConfigChannel.GROUP)
            publisher.publish(json.toString(), object : FrameworkConfigStore {
                @android.annotation.SuppressLint("UseKtx")
                override fun commit(snapshot: String): Boolean =
                    prefs.edit().putString(FrameworkConfigChannel.SNAPSHOT_KEY, snapshot).commit()

                override fun writeFile(name: String, payload: ByteArray) {
                    ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(name)).use { it.write(payload) }
                }

                override fun writeHead(snapshot: String) {
                    ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(FrameworkConfigChannel.HEAD_FILE)).use {
                        it.channel.truncate(0)
                        it.write(snapshot.toByteArray(Charsets.UTF_8))
                    }
                }

                override fun listFiles(): List<String> = service.listRemoteFiles().toList()
                override fun deleteFile(name: String) { service.deleteRemoteFile(name) }
            })
            publicationPending = false
            val now = System.currentTimeMillis()
            if (!transportPrefs.getBoolean("legacy_files_removed", false) && now >= nextCleanupAttemptAt) {
                nextCleanupAttemptAt = now + 60_000
                scope.launch {
                    if (rootManager.executeCommand(SystemFileCommands.removeLegacyConfigs()) != "ERROR") {
                        transportPrefs.edit { putBoolean("legacy_files_removed", true) }
                    }
                }
            }
            true
        } catch (error: Exception) {
            android.util.Log.e("LocationSpoofer", "Framework config publication failed", error)
            publicationPending = true
            if (notifyFailure) _writeFailures.tryEmit(Unit)
            false
        }
    }
}
