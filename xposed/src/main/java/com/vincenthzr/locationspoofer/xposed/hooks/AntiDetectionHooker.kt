package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookAntiDetection(classLoader: ClassLoader) {
    try {
        val appOpsClass = XposedHelpers.findClassIfExists("android.app.AppOpsManager", classLoader)
        if (appOpsClass != null) {
            val locationOpSet = setOf(
                0, 1, 2, 12, 41, 42, 59, 77,
                "android:coarse_location", "android:fine_location", "android:gps",
                "android:monitor_location", "android:monitor_location_high_power",
                "android:bluetooth_scan", "android:bluetooth_connect", "android:bluetooth_advertise"
            )

            val opMethods = arrayOf("checkOp", "checkOpNoThrow", "noteOp", "noteOpNoThrow", "noteProxyOp", "noteProxyOpNoThrow")
            for (mName in opMethods) {
                try {
                    XposedHelpers.hookAllMethods(appOpsClass, mName) { chain, _ ->
                        val config = readConfig()
                        if (config == null || !config.optBoolean("active", false)) {
                            return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                        }
                        val opArg = chain.args.getOrNull(0)
                        val isMockOp = when (opArg) {
                            58 -> true
                            "android:mock_location", "mock_location" -> true
                            else -> false
                        }
                        if (isMockOp) {
                            return@hookAllMethods 1
                        }

                        if (opArg != null && locationOpSet.contains(opArg)) {
                            return@hookAllMethods 0
                        }

                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (_: Throwable) {}
            }
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    try {
        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getInt",
                android.content.ContentResolver::class.java,
                String::class.java
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean("active", false)) {
                    return@hookMethod chain.proceed(chain.args.toTypedArray())
                }
                val name = chain.args.getOrNull(1) as? String
                if (name == "mock_location") {
                    return@hookMethod 0
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getInt",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean("active", false)) {
                    return@hookMethod chain.proceed(chain.args.toTypedArray())
                }
                val name = chain.args.getOrNull(1) as? String
                if (name == "mock_location") {
                    return@hookMethod 0
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getString",
                android.content.ContentResolver::class.java,
                String::class.java
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean("active", false)) {
                    return@hookMethod chain.proceed(chain.args.toTypedArray())
                }
                val name = chain.args.getOrNull(1) as? String
                if (name == "mock_location") {
                    return@hookMethod "0"
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    try {
        val locationPermissions = setOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION"
        )

        val bluetoothPermissions = setOf(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE"
        )
        val contextClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("android.content.Context", classLoader),
            XposedHelpers.findClassIfExists("android.content.ContextWrapper", classLoader),
            XposedHelpers.findClassIfExists("android.app.ContextImpl", classLoader)
        )
        val permMethods = arrayOf("checkPermission", "checkSelfPermission", "checkCallingOrSelfPermission", "checkCallingPermission")
        for (ctxClazz in contextClasses) {
            for (mName in permMethods) {
                try {
                    XposedHelpers.hookAllMethods(ctxClazz, mName) { chain, _ ->
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val perm = chain.args.firstOrNull { it is String } as? String
                            val shouldFake = perm != null && (
                                    locationPermissions.contains(perm) ||
                                            (bluetoothPermissions.contains(perm) &&
                                                    config.optBoolean("mock_bluetooth", false))
                                    )
                            if (shouldFake) {
                                return@hookAllMethods 0
                            }
                        }
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (_: Throwable) {}
            }
        }
    } catch (_: Throwable) {}

    try {
        val appPmClass = XposedHelpers.findClassIfExists("android.app.ApplicationPackageManager", classLoader)
        val pmClass = XposedHelpers.findClassIfExists("android.content.pm.PackageManager", classLoader)
        for (clazz in listOfNotNull(appPmClass, pmClass)) {
            try {
                XposedHelpers.hookAllMethods(clazz, "hasSystemFeature") { chain, _ ->
                    val feat = chain.args.firstOrNull { it is String } as? String
                    val isBtFeature = feat == "android.hardware.bluetooth_le" ||
                            feat == "android.hardware.bluetooth"
                    val isLocationFeature = feat == "android.hardware.location.gps" ||
                            feat == "android.hardware.location.network"
                    if (isBtFeature || isLocationFeature) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            if (isLocationFeature || config.optBoolean("mock_bluetooth", false)) {
                                return@hookAllMethods true
                            }
                        }
                    }
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}
        }
    } catch (_: Throwable) {}

    XposedBridge.log("[LocationSpoofer] Anti-detection hooks installed")
}
