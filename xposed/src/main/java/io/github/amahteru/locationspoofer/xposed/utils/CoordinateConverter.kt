package io.github.amahteru.locationspoofer.xposed.utils

import io.github.amahteru.locationspoofer.utils.CoordinateUtils
import io.github.amahteru.locationspoofer.xposed.LocationHooker
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

private val LLBAND = doubleArrayOf(75.0, 60.0, 45.0, 30.0, 15.0, 0.0)
private val LL2MC = arrayOf(
    doubleArrayOf(-0.0015702102444, 111320.7020616939, 1704480524535203.0, -10338987376042340.0, 26112667856603880.0, -35149669176653700.0, 26595700718403920.0, -10725012454188240.0, 1800819912950474.0, 82.5),
    doubleArrayOf(0.0008277824516172526, 111320.7020463578, 647795574.6671607, -4082003173.641316, 10774905663.51142, -15171875531.51559, 12053065338.62167, -5124939663.577472, 913311935.9512032, 67.5),
    doubleArrayOf(0.00337398766765, 111320.7020202162, 4481351.045890365, -23393751.19931662, 79682215.47186455, -115964993.2797253, 97236711.15602145, -43661946.33752821, 8477230.501135234, 52.5),
    doubleArrayOf(0.00220636496208, 111320.7020209128, 51751.86112841131, 3796837.749470245, 992013.7397791013, -1221952.21711287, 1340652.697009075, -620943.6990984312, 144416.9293806241, 37.5),
    doubleArrayOf(-0.0003441963504368392, 111320.7020576856, 278.2353980772752, 2485758.690035394, 6070.750963243378, 54821.18345352118, 9540.606633304236, -2710.55326746645, 1405.483844121726, 22.5),
    doubleArrayOf(-0.0003218135878613132, 111320.7020701615, 0.00369383431289, 823725.6402795718, 0.46104986909093, 2351.343141331292, 1.58060784298199, 8.77738589078284, 0.37238884252424, 7.45)
)

fun bd09llToBd09mc(bdLat: Double, bdLng: Double): Pair<Double, Double> {
    val latClamped = bdLat.coerceIn(-74.0, 74.0)
    val absLat = abs(latClamped)
    var cE = LL2MC.last()
    for (i in LLBAND.indices) {
        if (absLat >= LLBAND[i]) {
            cE = LL2MC[i]
            break
        }
    }
    var x = cE[0] + cE[1] * abs(bdLng)
    val cC = absLat / cE[9]
    var y = cE[2] + cE[3] * cC + cE[4] * cC * cC + cE[5] * cC * cC * cC +
            cE[6] * cC * cC * cC * cC + cE[7] * cC * cC * cC * cC * cC +
            cE[8] * cC * cC * cC * cC * cC * cC
    if (bdLng < 0) x = -x
    if (bdLat < 0) y = -y
    return Pair(y, x)
}

internal fun LocationHooker.getAppTargetCoordinate(
    rawGcjLat: Double,
    rawGcjLng: Double,
    defaultSystem: String = "GCJ-02"
): Pair<Double, Double> = when (defaultSystem) {
    "WGS-84" -> gcj02ToWgs84(rawGcjLat, rawGcjLng)
    "BD-09" -> gcj02ToBd09(rawGcjLat, rawGcjLng)
    else -> Pair(rawGcjLat, rawGcjLng)
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

internal fun LocationHooker.getJitteredVerticalAccuracy(): Float =
    (1.2 + 0.6 * hookAccuracyDrift + 0.05 * rng.nextGaussian()).coerceIn(0.8, 2.5).toFloat()

internal fun LocationHooker.getJitteredSpeedAccuracy(): Float =
    (0.1 + 0.05 * hookAccuracyDrift + 0.01 * rng.nextGaussian()).coerceIn(0.05, 0.3).toFloat()

internal fun LocationHooker.getJitteredBearingAccuracy(): Float =
    (1.5 + 0.8 * hookAccuracyDrift + 0.1 * rng.nextGaussian()).coerceIn(0.8, 3.0).toFloat()
