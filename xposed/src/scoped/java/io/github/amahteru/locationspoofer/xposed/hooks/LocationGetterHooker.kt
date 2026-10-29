package io.github.amahteru.locationspoofer.xposed.hooks

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookLocationGetters(classLoader: ClassLoader, currentPkg: String) {
    try {
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getLatitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "io.github.amahteru.locationspoofer") return@hookMethod result
            val motion = getCurrentSpoofedMotion(defaultCoordinateSystemOf(chain.thisObject))
            if (motion != null) {
                result = motion.lat
            }

            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getLongitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "io.github.amahteru.locationspoofer") return@hookMethod result
            val motion = getCurrentSpoofedMotion(defaultCoordinateSystemOf(chain.thisObject))
            if (motion != null) {
                result = motion.lng
            }

            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getAccuracy"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "io.github.amahteru.locationspoofer") return@hookMethod result
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = getJitteredAccuracy()
            }

            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "hasAccuracy"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = true
            }
            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getSpeed"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "io.github.amahteru.locationspoofer") return@hookMethod result
            val motion = getCurrentSpoofedMotion()
            if (motion != null) {
                result = motion.speed
            }

            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "hasSpeed"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = true
            }
            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getBearing"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "io.github.amahteru.locationspoofer") return@hookMethod result
            val motion = getCurrentSpoofedMotion()
            if (motion != null) {
                result = motion.bearing
            }

            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "hasBearing"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = true
            }
            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getAltitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val baseAlt = RouteEngine.realisticAltitude(config)
                val enableJitter = config.optBoolean("enable_jitter", true)
                result = if (enableJitter && baseAlt > 0.0) {
                    baseAlt + (rng.nextDouble() - 0.5)
                } else {
                    baseAlt
                }
            }

            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "hasAltitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = true
            }
            return@hookMethod result
        }

        try {
            XposedHelpers.hookMethod("android.location.Location", classLoader, "getSpeedAccuracyMetersPerSecond") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) getJitteredSpeedAccuracy() else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "hasSpeedAccuracy") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "getBearingAccuracyDegrees") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) getJitteredBearingAccuracy() else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "hasBearingAccuracy") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "getVerticalAccuracyMeters") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) getJitteredVerticalAccuracy() else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "hasVerticalAccuracy") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getTime"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") {
                result = System.currentTimeMillis()
            }
            return@hookMethod result
        }

        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "getElapsedRealtimeNanos"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") {
                    result = android.os.SystemClock.elapsedRealtimeNanos()
                }
                return@hookMethod result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "getElapsedRealtimeMillis"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") {
                    result = android.os.SystemClock.elapsedRealtime()
                }
                return@hookMethod result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "getElapsedRealtimeAgeMillis"
            ) { chain, method ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") 0L else chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "getExtras"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "io.github.amahteru.locationspoofer") {
                    val bundle = (result as? android.os.Bundle) ?: android.os.Bundle()
                    putSatelliteExtras(bundle, config.optInt("satellite_count", 20))
                    result = bundle
                }
                return@hookMethod result
            }
        } catch (_: Throwable) {}

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "isFromMockProvider"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = false
            }

            return@hookMethod result
        }
        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "isMock"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())

                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = false
                }

                return@hookMethod result
            }
        } catch (e: Throwable) {
        }

        XposedHelpers.hookMethod(
            "android.location.Location", classLoader, "getProvider"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val provider = result as? String ?: return@hookMethod result
                if (provider.contains("mock", ignoreCase = true) ||
                    provider.contains("test", ignoreCase = true) ||
                    provider.contains("fake", ignoreCase = true)
                ) {
                    result = android.location.LocationManager.GPS_PROVIDER
                }
            }
            return@hookMethod result
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }
}
