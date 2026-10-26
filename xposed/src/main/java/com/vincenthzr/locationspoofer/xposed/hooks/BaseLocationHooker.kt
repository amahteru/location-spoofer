package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*

/** 当前时刻的模拟位置（已换算到 [defaultSystem] 坐标系并叠加抖动）；未在模拟时返回 null。系统服务各 Hook 共用。 */
internal fun LocationHooker.getCurrentSpoofedMotion(defaultSystem: String = "GCJ-02"): SpoofedMotion? {
    val config = readConfig() ?: return null
    if (!config.optBoolean("active", false)) return null

    val rawMotion = RouteEngine.calculateCurrentPosition(config)
    val finalCoords = toCoordinateSystem(rawMotion.lat, rawMotion.lng, defaultSystem)
    val jittered = getJitteredLocation(finalCoords.first, finalCoords.second)
    return SpoofedMotion(jittered.first, jittered.second, rawMotion.bearing, rawMotion.speed)
}
