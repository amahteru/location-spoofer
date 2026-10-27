package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookAddressFields(clazz: Class<*>, classLoader: ClassLoader) {
    val stringMethods = arrayOf(
        "getCity",
        "getProvince",
        "getDistrict",
        "getAddress",
        "getAddrStr",
        "getCountry",
        "getNation",
        "getStreet",
        "getStreetNum",
        "getStreetNumber",
        "getStreetNo",
        "getCityCode",
        "getAdCode",
        "getPoiName",
        "getAoiName",
        "getTown",
        "getVillage",
        "getLocationDescribe"
    )
    for (methodName in stringMethods) {
        try {
            XposedHelpers.hookAllMethods(clazz, methodName) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                if (method !is Method) return@hookAllMethods result
                if (method.returnType != String::class.java) return@hookAllMethods result

                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val res = result as? String
                    if (res.isNullOrEmpty() || res.contains("Unknown", ignoreCase = true)) {
                        result = when (method.name) {
                            "getCity" -> cachedCity
                            "getProvince" -> cachedProvince
                            "getDistrict" -> cachedDistrict
                            "getAddress", "getAddrStr" -> cachedAddress
                            "getCountry", "getNation" -> cachedCountry
                            "getStreet" -> cachedStreet
                            "getStreetNum", "getStreetNumber", "getStreetNo" -> cachedStreetNum
                            "getCityCode" -> ""
                            "getAdCode" -> ""
                            "getPoiName" -> cachedPoiName
                            "getAoiName" -> cachedPoiName
                            "getTown" -> ""
                            "getVillage" -> ""
                            "getLocationDescribe" -> "在${cachedPoiName}附近"
                            else -> ""
                        }
                    }
                }
                return@hookAllMethods result
            }
        } catch (e: Throwable) {
        }
    }
}

internal fun LocationHooker.hookGoogleFusedLocation(classLoader: ClassLoader) {
    try {
        val clientClazz = XposedHelpers.findClass(
            "com.google.android.gms.location.FusedLocationProviderClient", classLoader
        )
        if (hookedCallbackClasses.putIfAbsent(clientClazz, true) != null) return

        XposedHelpers.hookAllMethods(clientClazz, "requestLocationUpdates") { chain, _ ->
            for (arg in chain.args) {
                if (arg == null) continue
                if (!LocationHooker.hasTypeByName(
                        arg.javaClass,
                        "com.google.android.gms.location.LocationCallback"
                    )
                ) continue
                capturedFusedLocationCallbacks.addIfAbsent(arg)
                val callbackClazz = arg.javaClass
                if (hookedCallbackClasses.putIfAbsent(callbackClazz, true) == null) {
                    try {
                        XposedHelpers.hookAllMethods(callbackClazz, "onLocationResult") { lChain, _ ->
                            val config = readConfig()
                            if (config != null && config.optBoolean("active", false) &&
                                currentPackageName.substringBefore(":") != "com.vincenthzr.locationspoofer"
                            ) {
                                val resultArg = lChain.args.getOrNull(0)
                                if (resultArg != null) {
                                    try {
                                        @Suppress("UNCHECKED_CAST")
                                        val locations =
                                            XposedHelpers.callMethod(resultArg, "getLocations") as? List<Any?>
                                        locations?.forEach { loc ->
                                            if (loc is android.location.Location) {
                                                val motion = getCurrentSpoofedMotion("WGS-84")
                                                if (motion != null) applySpoofedFix(loc, motion, config)
                                            }
                                        }
                                    } catch (_: Throwable) {}
                                }
                            }
                            return@hookAllMethods lChain.proceed(lChain.args.toTypedArray())
                        }
                    } catch (_: Throwable) {}
                }
            }
            return@hookAllMethods chain.proceed(chain.args.toTypedArray())
        }

        XposedHelpers.hookAllMethods(clientClazz, "removeLocationUpdates") { chain, _ ->
            for (arg in chain.args) {
                if (arg != null) capturedFusedLocationCallbacks.remove(arg)
            }
            return@hookAllMethods chain.proceed(chain.args.toTypedArray())
        }

        XposedBridge.log("[LocationSpoofer] Google FusedLocationProvider hooks installed")
    } catch (_: Throwable) {
        XposedBridge.log("[LocationSpoofer] Google Play Services location not found, skipped")
    }
}
