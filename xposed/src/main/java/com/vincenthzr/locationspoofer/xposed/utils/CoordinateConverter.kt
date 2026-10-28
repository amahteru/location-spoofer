package com.vincenthzr.locationspoofer.xposed.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import org.json.JSONObject
import kotlin.math.*

internal fun LocationHooker.wgs84ToGcj02(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.wgs84ToGcj02(wgsLat, wgsLng)
    return Pair(r.lat, r.lng)
}

internal fun LocationHooker.gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.gcj02ToWgs84(gcjLat, gcjLng)
    return Pair(r.lat, r.lng)
}

internal fun LocationHooker.gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.gcj02ToBd09(gcjLat, gcjLng)
    return Pair(r.lat, r.lng)
}

internal fun LocationHooker.bd09ToGcj02(bdLat: Double, bdLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.bd09ToGcj02(bdLat, bdLng)
    return Pair(r.lat, r.lng)
}

fun bd09llToBd09mc(bdLat: Double, bdLng: Double): Pair<Double, Double> {
    val x = bdLng * 20037508.342789244 / 180.0
    val latClamped = bdLat.coerceIn(-85.0511287798, 85.0511287798)
    var y = kotlin.math.ln(kotlin.math.tan((90.0 + latClamped) * Math.PI / 360.0)) / (Math.PI / 180.0)
    y = y * 20037508.342789244 / 180.0
    return Pair(y, x)
}

internal fun LocationHooker.getAppTargetCoordinate(
    rawGcjLat: Double,
    rawGcjLng: Double,
    config: JSONObject?,
    defaultSystem: String = "GCJ-02"
): Pair<Double, Double> {
    if (config == null) return when (defaultSystem) {
        "WGS-84" -> gcj02ToWgs84(rawGcjLat, rawGcjLng)
        "BD-09" -> gcj02ToBd09(rawGcjLat, rawGcjLng)
        else -> Pair(rawGcjLat, rawGcjLng)
    }
    val appSystems = config.optJSONObject("app_coordinate_systems")
    val basePkg = currentPackageName.substringBefore(":")

    val userConfigured = if (appSystems?.has(basePkg) == true) {
        appSystems.optString(basePkg, "AUTO")
    } else {
        "AUTO"
    }

    val targetSys = if (userConfigured == "AUTO" || userConfigured.isBlank()) {
        defaultSystem
    } else {
        userConfigured
    }

    return when (targetSys) {
        "WGS-84" -> gcj02ToWgs84(rawGcjLat, rawGcjLng)
        "BD-09" -> gcj02ToBd09(rawGcjLat, rawGcjLng)
        "GCJ-02" -> Pair(rawGcjLat, rawGcjLng)
        else -> when (defaultSystem) {
            "WGS-84" -> gcj02ToWgs84(rawGcjLat, rawGcjLng)
            "BD-09" -> gcj02ToBd09(rawGcjLat, rawGcjLng)
            else -> Pair(rawGcjLat, rawGcjLng)
        }
    }
}

internal fun LocationHooker.getJitteredLocation(
    baseLat: Double,
    baseLng: Double
): Pair<Double, Double> {
    val enableJitter = lastConfig?.optBoolean("enable_jitter", true) ?: true
    if (!enableJitter) return Pair(baseLat, baseLng)

    val now = System.currentTimeMillis()
    val dt = if (hookLastCallTime > 0) {
        ((now - hookLastCallTime) / 1000.0).coerceIn(0.01, 5.0)
    } else 1.0
    hookLastCallTime = now

    val sigma = 0.000002
    val alpha = 0.05

    hookDriftLat =
        (hookDriftLat + sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLat * dt)
            .coerceIn(-0.00004, 0.00004)
    hookDriftLng =
        (hookDriftLng + sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLng * dt)
            .coerceIn(-0.00004, 0.00004)

    return Pair(baseLat + hookDriftLat, baseLng + hookDriftLng)
}

internal fun LocationHooker.getJitteredAccuracy(): Float {
    hookAccuracyDrift += 0.1 * rng.nextGaussian() - 0.05 * hookAccuracyDrift
    return (2.2 + hookAccuracyDrift).coerceIn(1.5, 3.5).toFloat()
}

// 跟随水平精度的漂移一起波动，均值与原来的固定值一致
internal fun LocationHooker.getJitteredVerticalAccuracy(): Float =
    (1.2 + 0.6 * hookAccuracyDrift + 0.05 * rng.nextGaussian()).coerceIn(0.8, 2.5).toFloat()

internal fun LocationHooker.getJitteredSpeedAccuracy(): Float =
    (0.1 + 0.05 * hookAccuracyDrift + 0.01 * rng.nextGaussian()).coerceIn(0.05, 0.3).toFloat()

internal fun LocationHooker.getJitteredBearingAccuracy(): Float =
    (1.5 + 0.8 * hookAccuracyDrift + 0.1 * rng.nextGaussian()).coerceIn(0.8, 3.0).toFloat()
