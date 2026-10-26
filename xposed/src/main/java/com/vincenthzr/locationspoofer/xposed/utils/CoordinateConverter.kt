package com.vincenthzr.locationspoofer.xposed.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import org.json.JSONObject
import kotlin.math.*

/**
 * 坐标系转换工具类 (Coordinate Converter)
 * 
 * 上下文:
 * - WGS-84: 国际标准 GPS 坐标。
 * - GCJ-02 (火星坐标系): 中国国家测绘局制定的强制加密坐标，中国大陆的所有地图(高德、腾讯)均使用此坐标。
 * - BD-09: 百度在 GCJ-02 的基础上再次加密的坐标系。
 * 
 * 作用:
 * 提供各个坐标系之间的精确数学转换。
 */

/**
 * GCJ-02 转 WGS-84（采用高精度反向迭代逼近算法，误差小于 0.5 毫米）
 */
internal fun LocationHooker.gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.gcj02ToWgs84(gcjLat, gcjLng)
    return Pair(r.lat, r.lng)
}

// GCJ-02 转 BD-09 转换(百度坐标系)
internal fun LocationHooker.gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.gcj02ToBd09(gcjLat, gcjLng)
    return Pair(r.lat, r.lng)
}

/** 把配置里的 GCJ-02 坐标换算到目标坐标系（"WGS-84" / "BD-09"，其余按 GCJ-02 原样返回） */
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

    // sigma=0.000002度(约0.2米步长), alpha=0.05(均值回归)
    val sigma = 0.000002
    val alpha = 0.05

    // 使用 Ornstein-Uhlenbeck 过程生成自然偏移，并硬性限制在 4 米以内 (约 0.00004 度)
    hookDriftLat =
        (hookDriftLat + sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLat * dt)
            .coerceIn(-0.00004, 0.00004)
    hookDriftLng =
        (hookDriftLng + sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLng * dt)
            .coerceIn(-0.00004, 0.00004)

    return Pair(baseLat + hookDriftLat, baseLng + hookDriftLng)
}

internal fun LocationHooker.getJitteredAccuracy(): Float {
    // 精度值在真实优良室外环境(1.5m-3.5m)微弱起伏，运动与地图软件将判定为满格绿色强信号
    hookAccuracyDrift += 0.1 * rng.nextGaussian() - 0.05 * hookAccuracyDrift
    return (2.2 + hookAccuracyDrift).coerceIn(1.5, 3.5).toFloat()
}
