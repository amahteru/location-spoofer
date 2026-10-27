package com.vincenthzr.locationspoofer.xposed.hooks

import android.annotation.SuppressLint
import android.os.Binder
import com.vincenthzr.locationspoofer.xposed.utils.SpoofedMotion
import com.vincenthzr.locationspoofer.xposed.utils.XposedBridge
import com.vincenthzr.locationspoofer.xposed.utils.XposedHelpers
import org.json.JSONObject

@SuppressLint("StaticFieldLeak")
object SystemHookUtils {
    private val BASE_EXEMPT_PACKAGES = setOf(
        "com.vincenthzr.locationspoofer",
        "android",
        "system_server",
        "system",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.gms",
        "com.qualcomm.location",
        "com.qualcomm.atfwd",
        "com.android.ons",
        "com.android.location.fused",
    )

    val EXEMPT_PACKAGES: Set<String> by lazy {
        BASE_EXEMPT_PACKAGES + com.vincenthzr.locationspoofer.xposed.hooks.vendor.VendorRegistry.additionalExemptPackages
    }

    @Volatile
    private var cachedSystemContext: android.content.Context? = null

    @SuppressLint("PrivateApi")
    fun getSystemContext(): android.content.Context? {
        cachedSystemContext?.let { return it }
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
            val ctx = atClass.getMethod("getSystemContext").invoke(currentAt) as? android.content.Context
            cachedSystemContext = ctx
            ctx
        } catch (_: Throwable) {
            null
        }
    }

    fun resolveCallingPackages(serviceInstance: Any?, overrideUid: Int? = null): Set<String> {
        return try {
            val uid = if (overrideUid != null && overrideUid > 1000) overrideUid else Binder.getCallingUid()
            if (uid <= 1000) return emptySet()

            val sysCtx = getSystemContext()
            if (sysCtx != null) {
                val pkgs = sysCtx.packageManager.getPackagesForUid(uid)
                if (!pkgs.isNullOrEmpty()) {
                    return pkgs.toSet()
                }
            }

            if (serviceInstance != null) {
                val context = XposedHelpers.getObjectField(serviceInstance, "mContext") as? android.content.Context
                if (context != null) {
                    val pkgs = context.packageManager.getPackagesForUid(uid)
                    if (!pkgs.isNullOrEmpty()) {
                        return pkgs.toSet()
                    }
                }
            }
            emptySet()
        } catch (t: Throwable) {
            XposedBridge.log("[SysHook] resolveCallingPackages failed: $t")
            emptySet()
        }
    }

    fun extractCallerUid(args: List<Any?>): Int? {
        for (arg in args) {
            if (arg == null) continue
            val className = arg.javaClass.simpleName
            if (className.contains("Identity") || className.contains("Request") || className.contains("Registration") || className == "AttributionSource") {
                try {
                    val uid = XposedHelpers.callMethod(arg, "getUid") as? Int
                    if (uid != null && uid > 1000) return uid
                } catch (_: Throwable) {}
                try {
                    val uid = XposedHelpers.getIntField(arg, "mUid")
                    if (uid > 1000) return uid
                } catch (_: Throwable) {}
            }
        }
        return null
    }

    fun extractPackageName(args: List<Any?>): String? {
        for (arg in args) {
            if (arg == null) continue
            if (arg is String) {
                if (arg.contains(".") &&
                    !arg.startsWith("android.") &&
                    !arg.startsWith("com.android.server.") &&
                    arg != "gps" && arg != "network" && arg != "passive" && arg != "fused"
                ) {
                    return arg
                }
            }
            val className = arg.javaClass.simpleName
            if (className.contains("Identity") || className.contains("Request") || className.contains("Registration") || className == "AttributionSource") {
                try {
                    val pkg = XposedHelpers.callMethod(arg, "getPackageName") as? String
                    if (!pkg.isNullOrEmpty() && pkg.contains(".") && !pkg.startsWith("android.")) {
                        return pkg
                    }
                } catch (_: Throwable) {}
                try {
                    val pkg = XposedHelpers.getObjectField(arg, "mPackageName") as? String
                    if (!pkg.isNullOrEmpty() && pkg.contains(".") && !pkg.startsWith("android.")) {
                        return pkg
                    }
                } catch (_: Throwable) {}
            }
            if (arg is android.app.PendingIntent) {
                val pkg = arg.creatorPackage
                if (!pkg.isNullOrEmpty() && pkg.contains(".")) return pkg
            }
        }
        return null
    }

    fun extractProvider(args: List<Any?>): String {
        for (arg in args) {
            if (arg is String) {
                when (arg.lowercase()) {
                    "gps", "network", "passive", "fused" -> return arg
                }
            }
            if (arg != null && (arg.javaClass.simpleName.contains("Request") || arg.javaClass.simpleName.contains("Registration"))) {
                try {
                    val p = XposedHelpers.callMethod(arg, "getProvider") as? String
                    if (!p.isNullOrEmpty()) return p
                } catch (_: Throwable) {}
                try {
                    val p = XposedHelpers.getObjectField(arg, "mProvider") as? String
                    if (!p.isNullOrEmpty()) return p
                } catch (_: Throwable) {}
            }
        }
        return android.location.LocationManager.GPS_PROVIDER
    }

    fun resolveTargetPackages(config: JSONObject): Set<String> {
        return config.optJSONArray("system_hook_packages")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it, null) }.toSet()
        } ?: emptySet()
    }

    fun isTargetCaller(
        serviceInstance: Any?,
        explicitPackage: String?,
        config: JSONObject,
        overrideUid: Int? = null
    ): Boolean {
        if (!config.optBoolean("active", false)) return false

        if (config.optBoolean("system_hook_global_mode", false)) {
            return isGlobalModeCaller(serviceInstance, explicitPackage, overrideUid)
        }

        val uid = if (overrideUid != null && overrideUid > 1000) overrideUid else Binder.getCallingUid()
        val cleanPkg = explicitPackage?.substringBefore(":")?.trim()
        val callingPackages = exemptCallingPackages(serviceInstance, explicitPackage, cleanPkg, uid) ?: return false

        val targetPackages = resolveTargetPackages(config)
        if (targetPackages.isEmpty()) return false

        if (cleanPkg != null && targetPackages.contains(cleanPkg)) {
            logWhitelistMatch("clean:$uid:$cleanPkg", "[SysHook] Whitelist match: cleanPkg=$cleanPkg (explicitPkg=$explicitPackage, uid=$uid)")
            return true
        }

        if (explicitPackage != null && targetPackages.contains(explicitPackage)) {
            logWhitelistMatch("exp:$explicitPackage", "[SysHook] Whitelist match: explicitPkg=$explicitPackage (uid=$uid)")
            return true
        }

        val matched = callingPackages.filter { pkg ->
            targetPackages.contains(pkg) || targetPackages.contains(pkg.substringBefore(":"))
        }
        if (matched.isNotEmpty()) {
            logWhitelistMatch("call:${matched.first()}", "[SysHook] Whitelist match: callingPkgs=$matched (uid=$uid)")
            return true
        }

        return false
    }

    fun isGlobalModeCaller(serviceInstance: Any?, explicitPackage: String?, overrideUid: Int? = null): Boolean {
        val uid = if (overrideUid != null && overrideUid > 1000) overrideUid else Binder.getCallingUid()
        if (uid < 10000) return false
        val cleanPkg = explicitPackage?.substringBefore(":")?.trim()
        val callingPackages = exemptCallingPackages(serviceInstance, explicitPackage, cleanPkg, uid) ?: return false
        return cleanPkg != null || callingPackages.isNotEmpty()
    }

    private fun exemptCallingPackages(serviceInstance: Any?, explicitPackage: String?, cleanPkg: String?, uid: Int): Set<String>? {
        if (cleanPkg == "com.vincenthzr.locationspoofer" || explicitPackage == "com.vincenthzr.locationspoofer") return null
        if (uid <= 1000 && explicitPackage == null) return null
        if (cleanPkg != null && EXEMPT_PACKAGES.contains(cleanPkg)) return null
        val callingPackages = resolveCallingPackages(serviceInstance, uid)
        if (callingPackages.any { EXEMPT_PACKAGES.contains(it.substringBefore(":")) }) return null
        return callingPackages
    }

    private val lastWhitelistLogTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun logWhitelistMatch(key: String, message: String) {
        val now = System.currentTimeMillis()
        val last = lastWhitelistLogTimes[key] ?: 0L
        if (now - last > 5000L) {
            lastWhitelistLogTimes[key] = now
            XposedBridge.log(message)
        }
    }

    fun applyFakeLocationFields(
        locObj: Any,
        motion: SpoofedMotion,
        altitude: Double,
        accuracy: Float
    ) {
        try {
            XposedHelpers.callMethod(locObj, "setLatitude", motion.lat)
            XposedHelpers.callMethod(locObj, "setLongitude", motion.lng)
            XposedHelpers.callMethod(locObj, "setAccuracy", accuracy)
            XposedHelpers.callMethod(locObj, "setSpeed", motion.speed)
            XposedHelpers.callMethod(locObj, "setBearing", motion.bearing)
            XposedHelpers.callMethod(locObj, "setAltitude", altitude)
            XposedHelpers.callMethod(locObj, "setTime", System.currentTimeMillis())
            XposedHelpers.callMethod(
                locObj, "setElapsedRealtimeNanos",
                android.os.SystemClock.elapsedRealtimeNanos()
            )
            try {
                XposedHelpers.callMethod(locObj, "setVerticalAccuracyMeters", 1.5f)
            } catch (_: Throwable) {}
            try {
                XposedHelpers.callMethod(locObj, "setSpeedAccuracyMetersPerSecond", 0.3f)
            } catch (_: Throwable) {}
            try {
                XposedHelpers.callMethod(locObj, "setBearingAccuracyDegrees", 2.0f)
            } catch (_: Throwable) {}
            try {
                XposedHelpers.callMethod(locObj, "setMock", false)
            } catch (_: Throwable) {}
            try {
                XposedHelpers.callMethod(locObj, "setIsFromMockProvider", false)
            } catch (_: Throwable) {}
            try {
                val extras = XposedHelpers.callMethod(locObj, "getExtras") as? android.os.Bundle
                if (extras != null) {
                    extras.remove("mockLocation")
                    extras.putInt("satellites", 20)
                    extras.putInt("satellites_in_view", 20)
                    extras.putInt("satellites_used_in_fix", 18)
                } else {
                    val bundle = android.os.Bundle()
                    bundle.putInt("satellites", 20)
                    bundle.putInt("satellites_in_view", 20)
                    bundle.putInt("satellites_used_in_fix", 18)
                    XposedHelpers.callMethod(locObj, "setExtras", bundle)
                }
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    fun buildFakeLocation(
        classLoader: ClassLoader,
        provider: String,
        motion: SpoofedMotion,
        altitude: Double,
        accuracy: Float
    ): Any? {
        return try {
            val locClass = Class.forName("android.location.Location", false, classLoader)
            val fakeLoc = locClass.getConstructor(String::class.java).newInstance(provider)
            applyFakeLocationFields(fakeLoc, motion, altitude, accuracy)
            fakeLoc
        } catch (t: Throwable) {
            XposedBridge.log("[SysHook] buildFakeLocation failed: $t")
            null
        }
    }
}
