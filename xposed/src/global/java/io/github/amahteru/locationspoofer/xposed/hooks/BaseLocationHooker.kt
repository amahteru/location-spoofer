package io.github.amahteru.locationspoofer.xposed.hooks

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*

internal fun LocationHooker.getCurrentSpoofedMotion(defaultSystem: String = "GCJ-02"): SpoofedMotion? {
    val config = readConfig() ?: return null
    if (!config.optBoolean("active", false)) return null

    val rawMotion = RouteEngine.calculateCurrentPosition(config)
    val finalCoords = toCoordinateSystem(rawMotion.lat, rawMotion.lng, defaultSystem)
    val jittered = getJitteredLocation(finalCoords.first, finalCoords.second)
    return SpoofedMotion(jittered.first, jittered.second, rawMotion.bearing, rawMotion.speed)
}
