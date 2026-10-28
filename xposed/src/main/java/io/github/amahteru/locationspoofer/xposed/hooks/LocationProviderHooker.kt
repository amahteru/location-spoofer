package io.github.amahteru.locationspoofer.xposed.hooks

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookLocationProviders(classLoader: ClassLoader, currentPkg: String) {
    try {
        try {
            val locationManagerClazz =
                XposedHelpers.findClass("android.location.LocationManager", classLoader)
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "addNmeaListener"
            ) { chain, method ->

                val args = chain.args.toTypedArray()
                for (i in args.indices) {
                    val arg = args[i] ?: continue

                    val isOnNmea = try {
                        LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "android.location.OnNmeaMessageListener"
                        )
                    } catch (e: Exception) {
                        false
                    }

                    val isGpsNmea = try {
                        LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "android.location.GpsStatus\$NmeaListener"
                        )
                    } catch (e: Exception) {
                        false
                    }

                    if (isOnNmea) {
                        args[i] = nmeaListenerProxies.getOrPut(arg) { createOnNmeaMessageListenerProxy(arg, classLoader) }
                    } else if (isGpsNmea) {
                        args[i] = nmeaListenerProxies.getOrPut(arg) { createGpsStatusNmeaListenerProxy(arg, classLoader) }
                    }
                }

                return@hookAllMethods chain.proceed(args)
            }

            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "removeNmeaListener"
            ) { chain, method ->
                val args = chain.args.toTypedArray()
                for (i in args.indices) {
                    val arg = args[i] ?: continue
                    nmeaListenerProxies.remove(arg)?.let { args[i] = it }
                }
                return@hookAllMethods chain.proceed(args)
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        try {
            val locationManagerClazz =
                XposedHelpers.findClass("android.location.LocationManager", classLoader)
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "requestLocationUpdates"
            ) { chain, method ->

                for (arg in chain.args) {
                    if (arg == null) continue
                    val className = arg.javaClass.name
                    if (className == "java.lang.String" || className == "android.os.Looper" || className == "android.location.Criteria" || className == "android.location.LocationRequest") continue

                    try {
                        val isExcluded = LocationHooker.hasTypeByName(arg.javaClass, "com.amap.api.location.AMapLocationListener") ||
                                LocationHooker.hasTypeByName(arg.javaClass, "com.baidu.location.BDLocationListener") ||
                                LocationHooker.hasTypeByName(arg.javaClass, "com.baidu.location.BDAbstractLocationListener") ||
                                LocationHooker.hasTypeByName(arg.javaClass, "com.tencent.map.geolocation.TencentLocationListener")

                        val isListener = !isExcluded && (LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "android.location.LocationListener"
                        ) || LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "java.util.function.Consumer"
                        ) || LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "androidx.core.util.Consumer"
                        ))

                        if (isListener) {
                            capturedLocationListeners.addIfAbsent(arg)

                            val listenerClazz = arg.javaClass
                            if (hookedCallbackClasses.putIfAbsent(listenerClazz, true) == null) {
                                try {
                                    XposedHelpers.hookAllMethods(listenerClazz, "onLocationChanged") { lChain, _ ->
                                        val config = readConfig()
                                        if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") {
                                            val locations = when (val firstArg = lChain.args.firstOrNull()) {
                                                is android.location.Location -> listOf(firstArg)
                                                is List<*> -> firstArg.filterIsInstance<android.location.Location>()
                                                else -> emptyList()
                                            }
                                            for (loc in locations) {
                                                val motion = getCurrentSpoofedMotion(defaultCoordinateSystemOf(loc)) ?: break
                                                applySpoofedFix(loc, motion, config)
                                            }
                                        }
                                        return@hookAllMethods lChain.proceed(lChain.args.toTypedArray())
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                    } catch (e: Throwable) {
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "removeUpdates"
            ) { chain, method ->

                for (arg in chain.args) {
                    if (arg == null) continue
                    try {
                        capturedLocationListeners.remove(arg)
                    } catch (e: Throwable) {
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "getCurrentLocation"
            ) { chain, method ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") {
                    val provider = chain.args.getOrNull(0) as? String ?: "gps"
                    val motion = getCurrentSpoofedMotion(defaultCoordinateSystemOfProvider(provider))
                    if (motion != null) {
                        var consumerArg: Any? = null
                        var executorArg: java.util.concurrent.Executor? = null
                        for (arg in chain.args) {
                            if (arg is java.util.concurrent.Executor) {
                                executorArg = arg
                            } else if (arg != null && (arg.javaClass.name.contains("Consumer") || LocationHooker.hasTypeByName(arg.javaClass, "java.util.function.Consumer") || LocationHooker.hasTypeByName(arg.javaClass, "androidx.core.util.Consumer"))) {
                                consumerArg = arg
                            }
                        }
                        if (consumerArg != null) {
                            val fakeLoc = buildSpoofedLocation(provider, motion, config)

                            val runDispatch = Runnable {
                                try {
                                    XposedHelpers.callMethod(consumerArg, "accept", fakeLoc)
                                } catch (_: Throwable) {}
                            }
                            if (executorArg != null) {
                                executorArg.execute(runDispatch)
                            } else {
                                android.os.Handler(android.os.Looper.getMainLooper()).post(runDispatch)
                            }
                            return@hookAllMethods null
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] Failed to hook requestLocationUpdates/getCurrentLocation: $e")
        }

        try {
            val locationManagerClazz2 =
                XposedHelpers.findClass("android.location.LocationManager", classLoader)
            XposedHelpers.hookAllMethods(
                locationManagerClazz2,
                "getLastKnownLocation"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())

                val hostPkg = currentPkg.substringBefore(":")
                if (hostPkg == "io.github.amahteru.locationspoofer") return@hookAllMethods result

                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookAllMethods result
                val provider = chain.args.getOrNull(0) as? String ?: "gps"
                val motion = getCurrentSpoofedMotion(defaultCoordinateSystemOfProvider(provider)) ?: return@hookAllMethods result
                try {
                    result = buildSpoofedLocation(provider, motion, config)
                } catch (e2: Throwable) {
                    XposedBridge.log("[LocationSpoofer] getLastKnownLocation build error: $e2")
                }
                return@hookAllMethods result
            }
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] Failed to hook getLastKnownLocation: $e")
        }

        try {
            val lmClazz = XposedHelpers.findClass("android.location.LocationManager", classLoader)
            XposedHelpers.hookAllMethods(lmClazz, "isLocationEnabled") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") {
                    return@hookAllMethods true
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(lmClazz, "isProviderEnabled") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") {
                    return@hookAllMethods true
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            for (methodName in arrayOf("getAllProviders", "getProviders")) {
                XposedHelpers.hookAllMethods(lmClazz, methodName) { chain, _ ->
                    val result = chain.proceed(chain.args.toTypedArray())
                    val config = readConfig()
                    if (config == null || !config.optBoolean("active", false) || currentPkg.substringBefore(":") == "io.github.amahteru.locationspoofer") {
                        return@hookAllMethods result
                    }
                    val list = result as? List<*> ?: return@hookAllMethods result
                    val cleaned = list.filterIsInstance<String>().filterNot {
                        it.contains("mock", ignoreCase = true) ||
                                it.contains("test", ignoreCase = true) ||
                                it.contains("fake", ignoreCase = true)
                    }.toMutableList()
                    if (!cleaned.contains(android.location.LocationManager.GPS_PROVIDER)) {
                        cleaned.add(android.location.LocationManager.GPS_PROVIDER)
                    }
                    return@hookAllMethods cleaned
                }
            }
        } catch (_: Throwable) {}
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }
}
