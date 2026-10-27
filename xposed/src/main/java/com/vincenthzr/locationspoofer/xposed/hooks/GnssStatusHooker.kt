package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookGnssStatus(classLoader: ClassLoader) {
    try {
        val locationManagerClazz =
            XposedHelpers.findClass("android.location.LocationManager", classLoader)

        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "addGpsStatusListener"
            ) { chain, method ->
                val listener = chain.args[0]
                if (listener != null) {
                    val clazz = listener.javaClass
                    if (hookedCallbackClasses.putIfAbsent(clazz, true) == null) {
                        try {
                            XposedHelpers.hookAllMethods(
                                clazz,
                                "onGpsStatusChanged"
                            ) { innerChain, innerMethod ->
                                return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "registerGnssStatusCallback"
            ) { chain, method ->
                var callbackObj: Any? = null
                for (arg in chain.args) {
                    if (arg != null && LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "android.location.GnssStatus\$Callback"
                        )
                    ) {
                        callbackObj = arg
                        break
                    }
                }
                if (callbackObj != null) {
                    val clazz = callbackObj.javaClass
                    if (hookedCallbackClasses.putIfAbsent(clazz, true) == null) {
                        try {
                            XposedHelpers.hookAllMethods(
                                clazz,
                                "onSatelliteStatusChanged"
                            ) { innerChain, innerMethod ->
                                val statusObj = innerChain.args[0]
                                val config = readConfig()
                                if (config != null && config.optBoolean("active", false)) {
                                    val count = config.optInt("satellite_count", 20)
                                    val enableJitter = config.optBoolean("enable_jitter", true)
                                    val spoofedStatus = GnssFastMockEngine.getOrCreateSpoofedGnssStatus(
                                        classLoader = clazz.classLoader ?: classLoader,
                                        targetCount = count,
                                        enableJitter = enableJitter,
                                        fallbackOriginalObj = statusObj
                                    )
                                    if (spoofedStatus != null) {
                                        val args = innerChain.args.toTypedArray()
                                        args[0] = spoofedStatus
                                        return@hookAllMethods innerChain.proceed(args)
                                    }
                                }
                                return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        fun suppressRealCallbackWhenActive(callback: Any, vararg methodNames: String) {
            val clazz = callback.javaClass
            if (hookedCallbackClasses.putIfAbsent(clazz, true) != null) return
            for (methodName in methodNames) {
                try {
                    XposedHelpers.hookAllMethods(clazz, methodName) { innerChain, _ ->
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            return@hookAllMethods null
                        }
                        return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                    }
                } catch (_: Throwable) {
                }
            }
        }

        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "registerGnssMeasurementsCallback"
            ) { chain, method ->
                val callback = chain.args.firstOrNull { arg ->
                    arg != null && LocationHooker.hasTypeByName(
                        arg.javaClass,
                        "android.location.GnssMeasurementsEvent\$Callback"
                    )
                }
                if (callback != null) {
                    suppressRealCallbackWhenActive(callback, "onGnssMeasurementsReceived", "onStatusChanged")
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "registerGnssNavigationMessageCallback"
            ) { chain, method ->
                val callback = chain.args.firstOrNull { arg ->
                    arg != null && LocationHooker.hasTypeByName(
                        arg.javaClass,
                        "android.location.GnssNavigationMessage\$Callback"
                    )
                }
                if (callback != null) {
                    suppressRealCallbackWhenActive(callback, "onGnssNavigationMessageReceived", "onStatusChanged")
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "getGpsStatus"
            ) { chain, method ->
                val result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val count = config.optInt("satellite_count", 20)
                    val statusObj = result ?: chain.args.firstOrNull()
                    if (statusObj != null) {
                        try {
                            val satellites = GnssFastMockEngine.getOrCreateSpoofedGpsSatellites(classLoader, count)
                            XposedHelpers.setObjectField(statusObj, "mSatellites", satellites)
                        } catch (_: Throwable) {}
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {
        }

        try {
            XposedHelpers.hookMethod(
                "android.location.GpsStatus",
                classLoader,
                "getSatellites"
            ) { chain, method ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val count = config.optInt("satellite_count", 20)
                    return@hookMethod GnssFastMockEngine.getOrCreateSpoofedGpsSatellites(classLoader, count)
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        XposedBridge.log("[LocationSpoofer] GnssStatus hooks installed (High-Performance Engine)")
    } catch (e: Throwable) {
        XposedBridge.log("[LocationSpoofer] GnssStatus hook failed: $e")
    }

    readConfig()
}

internal fun LocationHooker.createSpoofedGpsSatellites(classLoader: ClassLoader): Iterable<Any> {
    val config = readConfig()
    val count = config?.optInt("satellite_count", 20) ?: 20
    return GnssFastMockEngine.getOrCreateSpoofedGpsSatellites(classLoader, count)
}
