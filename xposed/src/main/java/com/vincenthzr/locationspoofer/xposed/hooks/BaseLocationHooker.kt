package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.getCurrentSpoofedMotion(defaultSystem: String = "GCJ-02"): SpoofedMotion? {
    val config = readConfig() ?: return null
    if (!config.optBoolean("active", false)) return null

    val rawMotion = RouteEngine.calculateCurrentPosition(config)
    val finalCoords = getAppTargetCoordinate(rawMotion.lat, rawMotion.lng, config, defaultSystem)
    val jittered = getJitteredLocation(finalCoords.first, finalCoords.second)
    return SpoofedMotion(jittered.first, jittered.second, rawMotion.bearing, rawMotion.speed)
}

internal fun LocationHooker.hookLocationAPIs(classLoader: ClassLoader, currentPkg: String) {
    hookLocationGetters(classLoader, currentPkg)
    hookLocationProviders(classLoader, currentPkg)

    hookAllMapSdks(classLoader)
}

internal fun defaultCoordinateSystemOf(loc: Any?): String {
    val name = loc?.javaClass?.name ?: return "WGS-84"
    return when {
        name.contains("amap", ignoreCase = true) || name.contains("autonavi", ignoreCase = true) -> "GCJ-02"
        name.contains("baidu", ignoreCase = true) -> "BD-09"
        name.contains("tencent", ignoreCase = true) -> "GCJ-02"
        defaultCoordinateSystemOfProvider(try { (loc as? android.location.Location)?.provider } catch (_: Throwable) { null }) == "BD-09" -> "BD-09"
        else -> "WGS-84"
    }
}

internal fun defaultCoordinateSystemOfProvider(provider: String?): String =
    if (provider?.lowercase() == "baidu") "BD-09" else "WGS-84"

internal fun putSatelliteExtras(extras: android.os.Bundle, satCount: Int) {
    extras.putInt("satellites", satCount)
    extras.putInt("satellites_in_view", satCount)
    extras.putInt("satellites_used_in_fix", satCount)
    extras.putInt("satellites_visible", satCount)
}

internal fun clearMockFlag(loc: Any) {
    try { XposedHelpers.callMethod(loc, "setIsFromMockProvider", false) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(loc, "setMock", false) } catch (_: Throwable) {}
}

internal fun LocationHooker.applySpoofedFix(
    loc: android.location.Location,
    lat: Double,
    lng: Double,
    speed: Float,
    bearing: Float,
    accuracy: Float,
    config: org.json.JSONObject,
    time: Long = System.currentTimeMillis(),
    elapsedRealtimeNanos: Long = android.os.SystemClock.elapsedRealtimeNanos()
) {
    loc.latitude = lat
    loc.longitude = lng
    loc.accuracy = accuracy
    loc.speed = speed
    loc.bearing = bearing
    loc.altitude = RouteEngine.realisticAltitude(config)
    loc.time = time
    loc.elapsedRealtimeNanos = elapsedRealtimeNanos
    try {
        val extras = loc.extras ?: android.os.Bundle()
        putSatelliteExtras(extras, config.optInt("satellite_count", 20))
        loc.extras = extras
    } catch (_: Throwable) {}
    clearMockFlag(loc)
}

internal fun LocationHooker.applySpoofedFix(loc: android.location.Location, motion: SpoofedMotion, config: org.json.JSONObject) =
    applySpoofedFix(loc, motion.lat, motion.lng, motion.speed, motion.bearing, getJitteredAccuracy(), config)

internal fun LocationHooker.buildSpoofedLocation(provider: String?, motion: SpoofedMotion, config: org.json.JSONObject): android.location.Location =
    android.location.Location(provider?.takeIf { it.isNotBlank() } ?: android.location.LocationManager.GPS_PROVIDER)
        .also { applySpoofedFix(it, motion, config) }
