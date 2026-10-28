package io.github.amahteru.locationspoofer.xposed.hooks

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

private fun resolveBaiduCoordSystem(coorType: String?): Pair<String, Boolean> {
    XposedBridge.logOpenCellIdEvery(
        "baidu_coorType_${coorType ?: "null"}",
        "resolveBaiduCoordSystem coorType=$coorType"
    )
    return when (coorType?.lowercase()) {
        "wgs84" -> "WGS-84" to false
        "gcj02" -> "GCJ-02" to false
        "bd09mc" -> "BD-09" to true
        else -> "BD-09" to false
    }
}

internal fun LocationHooker.hookBaiduSDK(classLoader: ClassLoader) {
    val baiduLocClass = "com.baidu.location.BDLocation"

    val baiduClazz = XposedHelpers.findClassIfExists(baiduLocClass, classLoader)
    if (baiduClazz != null && hookedCallbackClasses.putIfAbsent(baiduClazz, true) == null) {
        try {
            try {
                XposedHelpers.hookAllConstructors(baiduClazz) { chain, constructor ->
                    val result = chain.proceed(chain.args.toTypedArray())
                    val bdLoc = chain.thisObject
                    if (bdLoc != null) {
                        val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val existingCoorType = try {
                            XposedHelpers.callMethod(bdLoc, "getCoorType") as? String
                        } catch (_: Throwable) { null }
                        val (defaultSys, isMercator) = resolveBaiduCoordSystem(existingCoorType)
                        val motion = getCurrentSpoofedMotion(defaultSys)
                        if (motion != null) {
                            val (injLat, injLng) = if (isMercator) {
                                bd09llToBd09mc(motion.lat, motion.lng)
                            } else {
                                motion.lat to motion.lng
                            }
                            try { XposedHelpers.setDoubleField(bdLoc, "mLatitude", injLat) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(bdLoc, "mLongitude", injLng) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(bdLoc, "latitude", injLat) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(bdLoc, "longitude", injLng) } catch (_: Throwable) {}
                            try { XposedHelpers.setObjectField(bdLoc, "mCoorType", if (isMercator) "bd09mc" else "bd09ll") } catch (_: Throwable) {}
                            try { XposedHelpers.setIntField(bdLoc, "mLocType", 61) } catch (_: Throwable) {}
                            try { XposedHelpers.setIntField(bdLoc, "locType", 61) } catch (_: Throwable) {}
                        }
                    }
                }
                return@hookAllConstructors result
            }
        } catch (_: Throwable) {}

        XposedHelpers.hookAllMethods(baiduClazz, "getLatitude") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val coorType = try {
                XposedHelpers.callMethod(chain.thisObject!!, "getCoorType") as? String
            } catch (e: Throwable) {
                null
            }
            val (defaultSys, isMercator) = resolveBaiduCoordSystem(coorType)
            val motion = getCurrentSpoofedMotion(defaultSys)
            if (motion != null) {
                result = if (isMercator) bd09llToBd09mc(motion.lat, motion.lng).first else motion.lat
            }
            return@hookAllMethods result
        }

        XposedHelpers.hookAllMethods(baiduClazz, "getLongitude") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val coorType = try {
                XposedHelpers.callMethod(chain.thisObject!!, "getCoorType") as? String
            } catch (e: Throwable) {
                null
            }
            val (defaultSys, isMercator) = resolveBaiduCoordSystem(coorType)
            val motion = getCurrentSpoofedMotion(defaultSys)
            if (motion != null) {
                result = if (isMercator) bd09llToBd09mc(motion.lat, motion.lng).second else motion.lng
            }
            return@hookAllMethods result
        }

        try {
            XposedHelpers.hookAllMethods(baiduClazz, "getCoorType") { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val currentCoor = result as? String
                    if (currentCoor.isNullOrEmpty() || currentCoor == "null") {
                        result = "bd09ll"
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {
        }

        XposedHelpers.hookAllMethods(baiduClazz, "getLocType") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val originalLocationType = result as? Int ?: 61
                if (originalLocationType == 161 || originalLocationType == 601) {
                    result = originalLocationType
                } else {
                    result = 61
                }
            }
            return@hookAllMethods result
        }

        try {
            XposedHelpers.hookAllMethods(baiduClazz, "getRadius") { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = getJitteredAccuracy()
                }
                return@hookAllMethods result
            }
        } catch (e: Throwable) {
        }

        try {
            XposedHelpers.hookAllMethods(baiduClazz, "getSpeed") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val motion = RouteEngine.calculateCurrentPosition(config)
                    motion.speed * 3.6f
                } else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(baiduClazz, "hasSpeed") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(baiduClazz, "getDirection") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val motion = RouteEngine.calculateCurrentPosition(config)
                    motion.bearing
                } else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(baiduClazz, "getGpsCheckStatus") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) 1 else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(baiduClazz, "getGpsAccuracyStatus") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) 1 else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(baiduClazz, "hasAddr") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(baiduClazz, "hasAddress") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(baiduClazz, "getTime") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                } else chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }

        try {
            XposedHelpers.hookAllMethods(baiduClazz, "getMockGps") { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = 0
                }
                return@hookAllMethods result
            }
        } catch (e: Throwable) {
        }

        try {
            XposedHelpers.hookAllMethods(baiduClazz, "getSatelliteNumber") { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = config.optInt("satellite_count", 20)
                }
                return@hookAllMethods result
            }
        } catch (e: Throwable) {
        }

        hookAddressFields(baiduClazz, classLoader)

        XposedBridge.log("[LocationSpoofer] BDLocation method hooks installed")
    } catch (e: Throwable) {
        XposedBridge.log("[LocationSpoofer] BDLocation method hook failed: $e")
    }
    }

    val locationClientClass = XposedHelpers.findClassIfExists(
        "com.baidu.location.LocationClient", classLoader
    )
    if (locationClientClass != null && hookedCallbackClasses.putIfAbsent(locationClientClass, true) == null) {
        try {
            XposedHelpers.hookAllMethods(
                locationClientClass,
                "registerLocationListener"
            ) { chain, method ->
                val listener = chain.args[0]
                if (listener != null) {
                    try {
                        val listenerClazz = listener.javaClass
                        if (hookedCallbackClasses.putIfAbsent(listenerClazz, true) == null) {
                            try {
                                XposedHelpers.hookAllMethods(listenerClazz, "onReceiveLocation") { innerChain, innerMethod ->
                                    val config = readConfig() ?: return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                    if (!config.optBoolean("active", false) || innerChain.args.isEmpty()) return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                    val bdLoc = innerChain.args[0] ?: return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                    spoofBdLocation(bdLoc)
                                    return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                }
                            } catch (_: Throwable) {}
                        }
                        if (LocationHooker.hasTypeByName(
                                listener.javaClass,
                                "com.baidu.location.BDAbstractLocationListener"
                            ) || LocationHooker.hasTypeByName(
                                listener.javaClass,
                                "com.baidu.location.BDLocationListener"
                            )
                        ) {
                            capturedBaiduListeners.addIfAbsent(listener)
                        }
                    } catch (e: Throwable) {
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(
                locationClientClass,
                "unRegisterLocationListener"
            ) { chain, method ->
                val listener = chain.args[0]
                if (listener != null) {
                    capturedBaiduListeners.remove(listener)
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(
                locationClientClass,
                "getLastKnownLocation"
            ) { chain, method ->
                val result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val existingCoorType = if (result != null) {
                        try { XposedHelpers.callMethod(result, "getCoorType") as? String } catch (_: Throwable) { null }
                    } else null
                    val (defaultSys, isMercator) = resolveBaiduCoordSystem(existingCoorType)
                    val motion = getCurrentSpoofedMotion(defaultSys)
                    if (motion != null) {
                        val (injLat, injLng) = if (isMercator) bd09llToBd09mc(motion.lat, motion.lng) else motion.lat to motion.lng
                        val injCoorType = if (isMercator) "bd09mc" else "bd09ll"
                        val bdLoc = result ?: try {
                            val bdLocClass = XposedHelpers.findClass("com.baidu.location.BDLocation", classLoader)
                            bdLocClass.getConstructor().newInstance()
                        } catch (_: Throwable) { null }
                        if (bdLoc != null) {
                            try { XposedHelpers.callMethod(bdLoc, "setLatitude", injLat) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(bdLoc, "setLongitude", injLng) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(bdLoc, "mLatitude", injLat) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(bdLoc, "mLongitude", injLng) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(bdLoc, "setLocType", 61) } catch (_: Throwable) {}
                            try { XposedHelpers.setIntField(bdLoc, "mLocType", 61) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(bdLoc, "setCoorType", injCoorType) } catch (_: Throwable) {}
                            try { XposedHelpers.setObjectField(bdLoc, "mCoorType", injCoorType) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(bdLoc, "setRadius", getJitteredAccuracy()) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(bdLoc, "setSatelliteNumber", 20) } catch (_: Throwable) {}
                            return@hookAllMethods bdLoc
                        }
                    }
                }
                return@hookAllMethods result
            }
        } catch (e: Throwable) {
        }
    }

    val myLocationDataClass = XposedHelpers.findClassIfExists("com.baidu.mapapi.map.MyLocationData", classLoader)
    XposedBridge.log(
        "[LocationSpoofer] MyLocationData class found=${myLocationDataClass != null} classLoader=$classLoader"
    )
    if (myLocationDataClass != null && hookedCallbackClasses.putIfAbsent(myLocationDataClass, true) == null) {
        try {
            XposedHelpers.hookAllConstructors(myLocationDataClass) { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                val obj = chain.thisObject
                if (obj != null) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val motion = getCurrentSpoofedMotion("BD-09")
                        if (motion != null) {
                            XposedBridge.logOpenCellIdEvery(
                                "baidu_myLocationData_ctor",
                                "MyLocationData constructor inject lat=${motion.lat} lng=${motion.lng}"
                            )
                            try { XposedHelpers.setDoubleField(obj, "latitude", motion.lat) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(obj, "longitude", motion.lng) } catch (_: Throwable) {}
                        }
                    }
                }
                return@hookAllConstructors result
            }
        } catch (_: Throwable) {}
    }

    val baiduMapClass = XposedHelpers.findClassIfExists("com.baidu.mapapi.map.BaiduMap", classLoader)
    XposedBridge.log(
        "[LocationSpoofer] BaiduMap class found=${baiduMapClass != null} classLoader=$classLoader"
    )
    if (baiduMapClass != null && hookedCallbackClasses.putIfAbsent(baiduMapClass, true) == null) {
        try {
            XposedHelpers.hookAllMethods(baiduMapClass, "setMyLocationData") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && chain.args.isNotEmpty()) {
                    val data = chain.args[0]
                    if (data != null) {
                        val motion = getCurrentSpoofedMotion("BD-09")
                        if (motion != null) {
                            val beforeLat = try { XposedHelpers.getObjectField(data, "latitude") } catch (_: Throwable) { null }
                            val beforeLng = try { XposedHelpers.getObjectField(data, "longitude") } catch (_: Throwable) { null }
                            XposedBridge.logOpenCellIdEvery(
                                "baidu_setMyLocationData",
                                "setMyLocationData before=($beforeLat,$beforeLng) inject=(${motion.lat},${motion.lng})"
                            )
                            try { XposedHelpers.setDoubleField(data, "latitude", motion.lat) } catch (_: Throwable) {}
                            try { XposedHelpers.setDoubleField(data, "longitude", motion.lng) } catch (_: Throwable) {}
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }
}

internal fun LocationHooker.spoofBdLocation(bdLoc: Any) {
    val config = readConfig() ?: return
    val coorType = try { XposedHelpers.callMethod(bdLoc, "getCoorType") as? String } catch (_: Throwable) { null }
    val (defaultSys, isMercator) = resolveBaiduCoordSystem(coorType)
    val motion = getCurrentSpoofedMotion(defaultSys) ?: return
    val (injLat, injLng) = if (isMercator) bd09llToBd09mc(motion.lat, motion.lng) else motion.lat to motion.lng
    val injCoorType = if (isMercator) "bd09mc" else "bd09ll"
    XposedBridge.logOpenCellIdEvery(
        "baidu_onReceiveLocation",
        "onReceiveLocation coorType=$coorType isMercator=$isMercator inject=($injLat,$injLng,$injCoorType)"
    )

    try { XposedHelpers.callMethod(bdLoc, "setLatitude", injLat) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(bdLoc, "setLongitude", injLng) } catch (_: Throwable) {}
    try { XposedHelpers.setDoubleField(bdLoc, "mLatitude", injLat) } catch (_: Throwable) {}
    try { XposedHelpers.setDoubleField(bdLoc, "mLongitude", injLng) } catch (_: Throwable) {}
    try { XposedHelpers.setDoubleField(bdLoc, "latitude", injLat) } catch (_: Throwable) {}
    try { XposedHelpers.setDoubleField(bdLoc, "longitude", injLng) } catch (_: Throwable) {}

    try { XposedHelpers.callMethod(bdLoc, "setLocType", 61) } catch (_: Throwable) {}
    try { XposedHelpers.setIntField(bdLoc, "mLocType", 61) } catch (_: Throwable) {}
    try { XposedHelpers.setIntField(bdLoc, "locType", 61) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(bdLoc, "setCoorType", injCoorType) } catch (_: Throwable) {}
    try { XposedHelpers.setObjectField(bdLoc, "mCoorType", injCoorType) } catch (_: Throwable) {}

    try { XposedHelpers.callMethod(bdLoc, "setRadius", getJitteredAccuracy()) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(bdLoc, "setSpeed", motion.speed * 3.6f) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(bdLoc, "setDirection", motion.bearing) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(bdLoc, "setSatelliteNumber", config.optInt("satellite_count", 20)) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(bdLoc, "setGpsCheckStatus", 1) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(bdLoc, "setMockGps", 0) } catch (_: Throwable) {}
    try { XposedHelpers.callMethod(bdLoc, "setTime", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())) } catch (_: Throwable) {}
}
