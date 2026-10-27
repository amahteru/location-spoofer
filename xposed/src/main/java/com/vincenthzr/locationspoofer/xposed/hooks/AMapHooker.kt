package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookAMapSDK(classLoader: ClassLoader) {
    val amapLocClass = "com.amap.api.location.AMapLocation"
    val amapLocClazz = XposedHelpers.findClassIfExists(amapLocClass, classLoader)

    if (amapLocClazz != null && hookedCallbackClasses.putIfAbsent(amapLocClazz, true) == null) {
        try {
            try {
                XposedHelpers.hookAllConstructors(amapLocClazz) { chain, _ ->
                    val result = chain.proceed(chain.args.toTypedArray())
                    val amapLoc = chain.thisObject
                    if (amapLoc != null) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val motion = getCurrentSpoofedMotion("GCJ-02")
                            if (motion != null) {
                                try { XposedHelpers.callMethod(amapLoc, "setLatitude", motion.lat) } catch (_: Throwable) {}
                                try { XposedHelpers.callMethod(amapLoc, "setLongitude", motion.lng) } catch (_: Throwable) {}
                                try { XposedHelpers.setDoubleField(amapLoc, "mLatitude", motion.lat) } catch (_: Throwable) {}
                                try { XposedHelpers.setDoubleField(amapLoc, "mLongitude", motion.lng) } catch (_: Throwable) {}
                                try { XposedHelpers.callMethod(amapLoc, "setLocationType", 1) } catch (_: Throwable) {}
                                try { XposedHelpers.callMethod(amapLoc, "setGpsAccuracyStatus", 1) } catch (_: Throwable) {}
                                try { XposedHelpers.callMethod(amapLoc, "setSatellites", config.optInt("satellite_count", 20)) } catch (_: Throwable) {}
                            }
                        }
                    }
                    return@hookAllConstructors result
                }
            } catch (_: Throwable) {}

            XposedHelpers.hookAllMethods(amapLocClazz, "getLatitude") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                if (currentPackageName.substringBefore(":") == "com.vincenthzr.locationspoofer") return@hookAllMethods result
                val motion = getCurrentSpoofedMotion("GCJ-02")
                if (motion != null) {
                    result = motion.lat
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getLongitude") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                if (currentPackageName.substringBefore(":") == "com.vincenthzr.locationspoofer") return@hookAllMethods result
                val motion = getCurrentSpoofedMotion("GCJ-02")
                if (motion != null) {
                    result = motion.lng
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getAccuracy") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                if (currentPackageName.substringBefore(":") == "com.vincenthzr.locationspoofer") return@hookAllMethods result
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = getJitteredAccuracy()
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getSpeed") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                if (currentPackageName.substringBefore(":") == "com.vincenthzr.locationspoofer") return@hookAllMethods result
                val motion = getCurrentSpoofedMotion("GCJ-02")
                if (motion != null) {
                    result = motion.speed
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getBearing") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                if (currentPackageName.substringBefore(":") == "com.vincenthzr.locationspoofer") return@hookAllMethods result
                val motion = getCurrentSpoofedMotion("GCJ-02")
                if (motion != null) {
                    result = motion.bearing
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getSatellites") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = config.optInt("satellite_count", 20)
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getGpsAccuracyStatus") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = 1
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getLocationType") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = 1
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getAltitude") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = RouteEngine.realisticAltitude(config)
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getTime") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = System.currentTimeMillis()
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getErrorCode") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = 0
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "isMock") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = false
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "isFixLastLocation") { chain, _ ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = false
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(amapLocClazz, "getMockData") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    return@hookAllMethods null
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            hookAddressFields(amapLocClazz, classLoader)
            XposedBridge.log("[LocationSpoofer] AMapLocation hooks installed on ${amapLocClazz.name}")
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] AMapLocation hook failed: $e")
        }
    }

    val clientClazz = XposedHelpers.findClassIfExists(
        "com.amap.api.location.AMapLocationClient", classLoader
    )
    if (clientClazz != null && hookedCallbackClasses.putIfAbsent(clientClazz, true) == null) {
        try {
            XposedHelpers.hookAllMethods(clientClazz, "setLocationListener") { chain, _ ->
                val listener = chain.args.getOrNull(0)
                if (listener != null) {
                    try {
                        val listenerClazz = listener.javaClass
                        if (hookedCallbackClasses.putIfAbsent(listenerClazz, true) == null) {
                            try {
                                XposedHelpers.hookAllMethods(listenerClazz, "onLocationChanged") { innerChain, _ ->
                                    val config = readConfig() ?: return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                    if (!config.optBoolean("active", false) || innerChain.args.isEmpty()) return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                    val amapLoc = innerChain.args[0] ?: return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                    val motion = getCurrentSpoofedMotion("GCJ-02") ?: return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                    try { XposedHelpers.callMethod(amapLoc, "setLatitude", motion.lat) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setLongitude", motion.lng) } catch (_: Throwable) {}
                                    try { XposedHelpers.setDoubleField(amapLoc, "mLatitude", motion.lat) } catch (_: Throwable) {}
                                    try { XposedHelpers.setDoubleField(amapLoc, "mLongitude", motion.lng) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setAccuracy", getJitteredAccuracy()) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setSpeed", motion.speed) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setBearing", motion.bearing) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setAltitude", RouteEngine.realisticAltitude(config)) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setTime", System.currentTimeMillis()) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setSatellites", config.optInt("satellite_count", 20)) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setGpsAccuracyStatus", 1) } catch (_: Throwable) {}
                                    try { XposedHelpers.callMethod(amapLoc, "setLocationType", 1) } catch (_: Throwable) {}
                                    return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                }
                            } catch (_: Throwable) {}
                        }
                        if (LocationHooker.hasTypeByName(listener.javaClass, "com.amap.api.location.AMapLocationListener")) {
                            capturedAMapListeners.addIfAbsent(listener)
                        }
                    } catch (_: Throwable) {}
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(clientClazz, "unRegisterLocationListener") { chain, _ ->
                val listener = chain.args.getOrNull(0)
                if (listener != null) {
                    capturedAMapListeners.remove(listener)
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(clientClazz, "getLastKnownLocation") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val motion = getCurrentSpoofedMotion("GCJ-02")
                    if (motion != null) {
                        val amapLoc = result ?: try {
                            val locCls = XposedHelpers.findClass("com.amap.api.location.AMapLocation", classLoader)
                            locCls.getConstructor(String::class.java).newInstance("gps")
                        } catch (_: Throwable) { null }
                        if (amapLoc != null) {
                            try { XposedHelpers.callMethod(amapLoc, "setLatitude", motion.lat) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(amapLoc, "setLongitude", motion.lng) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(amapLoc, "mLatitude", motion.lat) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(amapLoc, "mLongitude", motion.lng) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(amapLoc, "setLocationType", 1) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(amapLoc, "setGpsAccuracyStatus", 1) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(amapLoc, "setSatellites", config.optInt("satellite_count", 20)) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(amapLoc, "setRadius", getJitteredAccuracy()) } catch (_: Throwable) {}
                            return@hookAllMethods amapLoc
                        }
                    }
                }
                return@hookAllMethods result
            }

            XposedHelpers.hookAllMethods(clientClazz, "setMockEnable") { chain, _ ->
                val config = readConfig()
                val args = chain.args.toTypedArray()
                if (config != null && config.optBoolean("active", false) && args.isNotEmpty()) {
                    args[0] = true
                }
                return@hookAllMethods chain.proceed(args)
            }
        } catch (_: Throwable) {}
    }

    val reportClazz = XposedHelpers.findClassIfExists(
        "com.amap.api.location.AMapLocationQualityReport", classLoader
    )
    if (reportClazz != null && hookedCallbackClasses.putIfAbsent(reportClazz, true) == null) {
        try {
            XposedHelpers.hookAllMethods(reportClazz, "isWifiAble") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(reportClazz, "isInstalledHighDangerMockApp") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) false else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(reportClazz, "getGPSSatellites") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) config.optInt("satellite_count", 20) else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(reportClazz, "getGPSStatus") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) 0 else chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }
}
