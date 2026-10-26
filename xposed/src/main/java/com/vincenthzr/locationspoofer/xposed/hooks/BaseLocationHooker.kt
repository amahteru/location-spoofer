package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

/**
 * 基础定位框架拦截模块 (Base Location Hooker)
 * 
 * 上下文:
 * Android 系统的原生定位服务由 `android.location.LocationManager` 提供，包含 GPS 卫星状态 (GnssStatus)、
 * NMEA 报文 (底层 GPS 硬件输出的数据格式) 以及基础坐标。
 * 
 * 作用:
 * 本模块是位置伪造的核心，直接 Hook Android 原生系统的底层接口，从而对所有的 App 生效。
 * 关键部分解释:
 * 1. hookLocationAPIs: 拦截 LocationManager 的 requestLocationUpdates 等方法，不仅伪造坐标，
 *    还会使用 Ornstein-Uhlenbeck 随机过程增加自然的坐标抖动，并清除 Location 对象的 `isFromMockProvider` 标志，
 *    这是绕过绝大多数检测的关键。
 * 2. hookGnssStatus / createSpoofedGpsSatellites: 伪造卫星数据。如果只伪造坐标但不伪造天上的卫星，
 *    高德/百度SDK会轻易发现异常（坐标在变但搜不到卫星）。这里动态生成了一套信噪比(SNR)和仰角逼真的卫星阵列。
 * 3. NMEA 拦截: NMEA-0183 报文是底层 GPS 芯片吐出的串口数据，高级的地图 SDK 会直接解析 NMEA 而非 Location 对象，
 *    所以我们必须在此处把伪造的经纬度按照 NMEA 格式 (GPGGA, GPRMC 等) 重新编码并注入。
 */


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

    // 第三方地图SDK深度Hook(高德/腾讯/百度)
    hookAllMapSdks(classLoader)
}

/**
 * 按 Location 的实际类型推断它默认使用的坐标系：高德 / 腾讯的子类是 GCJ-02，百度是 BD-09，
 * 其余按 Android 规范是 WGS-84。
 */
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

/** 往定位 extras 里写卫星数等元数据，防止百度 / 高德因卫星数为 0 判定为无定位信号而丢弃坐标 */
internal fun putSatelliteExtras(extras: android.os.Bundle, satCount: Int) {
    extras.putInt("satellites", satCount)
    extras.putInt("satellites_in_view", satCount)
    extras.putInt("satellites_used_in_fix", satCount.coerceAtLeast(12))
    extras.putInt("satellites_visible", satCount)
    extras.putBoolean("mockLocation", false)
}

/** 清掉 Location 的模拟标志位（Android 6~11 是 setIsFromMockProvider，12+ 是 setMock） */
internal fun clearMockFlag(loc: Any) {
    try { XposedHelpers.callMethod(loc, "setIsFromMockProvider", false) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(loc, "setMock", false) } catch (_: Throwable) {}
}

/** 把模拟的位置、速度、航向、海拔、时间戳和卫星元数据写进已有的 Location 对象 */
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

/** 用模拟的运动状态新建一个 Location */
internal fun LocationHooker.buildSpoofedLocation(provider: String?, motion: SpoofedMotion, config: org.json.JSONObject): android.location.Location =
    android.location.Location(provider?.takeIf { it.isNotBlank() } ?: android.location.LocationManager.GPS_PROVIDER)
        .also { applySpoofedFix(it, motion, config) }
