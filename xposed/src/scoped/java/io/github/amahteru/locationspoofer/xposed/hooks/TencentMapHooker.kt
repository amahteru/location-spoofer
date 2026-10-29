package io.github.amahteru.locationspoofer.xposed.hooks

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookTencentSDK(classLoader: ClassLoader) {
    val implCandidates = listOf(
        "com.tencent.map.geolocation.internal.TencentLocationImpl",
        "com.tencent.map.geolocation.TencentLocationImpl",
        "com.tencent.tencentmap.mapsdk.map.model.TencentLocationImpl"
    )

    var hooked = false
    for (implClass in implCandidates) {
        val clazz = XposedHelpers.findClassIfExists(implClass, classLoader)
        if (clazz != null) {
            if (hookedCallbackClasses.putIfAbsent(clazz, true) == null) {
                hookTencentLocationClass(clazz, classLoader)
            }
            hooked = true
            XposedBridge.log("[LocationSpoofer] TencentLocation impl found: $implClass")
            break
        }
    }

    if (!hooked) {
        val interfaceClazz = XposedHelpers.findClassIfExists(
            "com.tencent.map.geolocation.TencentLocation", classLoader
        )
        if (interfaceClazz != null && interfaceClazz.isInterface) {
            hooked = true
        } else if (interfaceClazz != null) {
            if (hookedCallbackClasses.putIfAbsent(interfaceClazz, true) == null) {
                hookTencentLocationClass(interfaceClazz, classLoader)
            }
            hooked = true
        }
    }

    if (!hooked) {
        XposedBridge.log("[LocationSpoofer] TencentLocation SDK not found, skipped")
    }

    val tencentManagerClass = XposedHelpers.findClassIfExists(
        "com.tencent.map.geolocation.TencentLocationManager", classLoader
    )
    if (tencentManagerClass != null && hookedCallbackClasses.putIfAbsent(tencentManagerClass, true) == null) {
        try {
            XposedHelpers.hookAllMethods(
                tencentManagerClass,
                "requestLocationUpdates"
            ) { chain, method ->
                for (arg in chain.args) {
                    if (arg != null) {
                        try {
                            if (LocationHooker.hasTypeByName(
                                    arg.javaClass,
                                    "com.tencent.map.geolocation.TencentLocationListener"
                                )
                            ) {
                                capturedTencentListeners.addIfAbsent(arg)
                                hookTencentListenerClass(arg.javaClass)
                            }
                        } catch (e: Throwable) {
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookAllMethods(tencentManagerClass, "removeUpdates") { chain, _ ->
                chain.args.forEach { if (it != null) capturedTencentListeners.remove(it) }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }
    }
}

internal fun LocationHooker.hookTencentLocationClass(clazz: Class<*>, classLoader: ClassLoader) {
    try {
        XposedHelpers.hookAllMethods(clazz, "getLatitude") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val motion = getCurrentSpoofedMotion("GCJ-02")
            if (motion != null) {
                result = motion.lat
            }
            return@hookAllMethods result
        }
        XposedHelpers.hookAllMethods(clazz, "getLongitude") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val motion = getCurrentSpoofedMotion("GCJ-02")
            if (motion != null) {
                result = motion.lng
            }
            return@hookAllMethods result
        }
        XposedHelpers.hookAllMethods(clazz, "getSpeed") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val motion = RouteEngine.calculateCurrentPosition(config)
                result = motion.speed
            }
            return@hookAllMethods result
        }
        XposedHelpers.hookAllMethods(clazz, "getBearing") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val motion = RouteEngine.calculateCurrentPosition(config)
                result = motion.bearing
            }
            return@hookAllMethods result
        }
        XposedHelpers.hookAllMethods(clazz, "getTime") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = System.currentTimeMillis()
            }
            return@hookAllMethods result
        }
    } catch (e: Throwable) {
        XposedBridge.log("[LocationSpoofer] TencentLocation class hook failed: $e")
        return
    }

    try {
        XposedHelpers.hookAllMethods(clazz, "getProvider") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val originalProvider = result as? String ?: "gps"
                if (originalProvider == "network" || originalProvider.contains(
                        "wifi",
                        ignoreCase = true
                    )
                ) {
                    result = originalProvider
                } else {
                    result = "gps"
                }
            }
            return@hookAllMethods result
        }
    } catch (e: Throwable) {
    }

    try {
        XposedHelpers.hookAllMethods(clazz, "getAccuracy") { chain, method ->
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
        XposedHelpers.hookAllMethods(clazz, "isMockGps") { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = 0
            }
            return@hookAllMethods result
        }
    } catch (e: Throwable) {
    }

    hookAddressFields(clazz, classLoader)

    XposedBridge.log("[LocationSpoofer] TencentLocation hooks installed on ${clazz.name}")
}

internal fun LocationHooker.hookTencentListenerClass(listenerClass: Class<*>) {
    if (hookedCallbackClasses.putIfAbsent(listenerClass, true) != null) return
    try {
        XposedHelpers.hookAllMethods(listenerClass, "onLocationChanged") { chain, method ->
            val config = readConfig()
            if (config == null || !config.optBoolean("active", false)) return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            val tencentLoc =
                chain.args.getOrNull(0) ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            val motion = getCurrentSpoofedMotion("GCJ-02") ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())

            try {
                XposedHelpers.callMethod(tencentLoc, "setLatitude", motion.lat)
            } catch (e: Throwable) {
                try {
                    XposedHelpers.setDoubleField(tencentLoc, "latitude", motion.lat)
                } catch (e2: Throwable) {
                    try {
                        XposedHelpers.setDoubleField(
                            tencentLoc,
                            "mLatitude",
                            motion.lat
                        )
                    } catch (e3: Throwable) {
                        try {
                            XposedHelpers.setDoubleField(
                                tencentLoc,
                                "a",
                                motion.lat
                            )
                        } catch (e4: Throwable) {
                        }
                    }
                }
            }
            try {
                XposedHelpers.callMethod(tencentLoc, "setLongitude", motion.lng)
            } catch (e: Throwable) {
                try {
                    XposedHelpers.setDoubleField(
                        tencentLoc,
                        "longitude",
                        motion.lng
                    )
                } catch (e2: Throwable) {
                    try {
                        XposedHelpers.setDoubleField(
                            tencentLoc,
                            "mLongitude",
                            motion.lng
                        )
                    } catch (e3: Throwable) {
                        try {
                            XposedHelpers.setDoubleField(
                                tencentLoc,
                                "b",
                                motion.lng
                            )
                        } catch (e4: Throwable) {
                        }
                    }
                }
            }
            return@hookAllMethods chain.proceed(chain.args.toTypedArray())
        }
        XposedBridge.log("[LocationSpoofer] TencentLocationListener callback hook installed on ${listenerClass.name}")
    } catch (e: Throwable) {
        XposedBridge.log("[LocationSpoofer] TencentLocationListener hook failed: $e")
    }
}
