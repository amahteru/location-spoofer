package com.vincenthzr.locationspoofer.xposed.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import org.json.JSONObject
import kotlin.math.*

internal fun LocationHooker.gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.gcj02ToWgs84(gcjLat, gcjLng)
    return Pair(r.lat, r.lng)
}

internal fun LocationHooker.gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.gcj02ToBd09(gcjLat, gcjLng)
    return Pair(r.lat, r.lng)
}

internal fun LocationHooker.toCoordinateSystem(gcjLat: Double, gcjLng: Double, system: String): Pair<Double, Double> =
    when (system) {
        "WGS-84" -> gcj02ToWgs84(gcjLat, gcjLng)
        "BD-09" -> gcj02ToBd09(gcjLat, gcjLng)
        else -> Pair(gcjLat, gcjLng)
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
