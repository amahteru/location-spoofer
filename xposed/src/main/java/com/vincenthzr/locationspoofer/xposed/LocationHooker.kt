package com.vincenthzr.locationspoofer.xposed

import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import com.vincenthzr.locationspoofer.xposed.hooks.network.*

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONObject
import java.io.File
import android.os.ParcelFileDescriptor
import com.vincenthzr.locationspoofer.utils.FrameworkConfigChannel
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.*
import java.util.concurrent.CopyOnWriteArrayList

class LocationHooker : XposedModule() {
    init {
        XposedHelpers.module = this
    }

    internal val nmeaListenerProxies = ConcurrentHashMap<Any, Any>()
    internal val bleScanTimers = ConcurrentHashMap<Any, java.util.Timer>()
    internal val hookedCallbackClasses = ConcurrentHashMap<Class<*>, Boolean>()

    @Volatile
    internal var environmentHooksInstalled = false

    internal val capturedLocationListeners = CopyOnWriteArrayList<Any>()
    internal val capturedAMapListeners = CopyOnWriteArrayList<Any>()
    internal val capturedBaiduListeners = CopyOnWriteArrayList<Any>()
    internal val capturedTencentListeners = CopyOnWriteArrayList<Any>()
    internal val capturedFusedLocationCallbacks = CopyOnWriteArrayList<Any>()

    @Volatile
    internal var currentPackageName: String = ""

    @Volatile
    internal var currentClassLoader: ClassLoader? = null

    @Volatile
    internal var cachedProvince = ""

    @Volatile
    internal var cachedCity = ""

    @Volatile
    internal var cachedDistrict = ""

    @Volatile
    internal var cachedStreet = ""

    @Volatile
    internal var cachedStreetNum = ""

    @Volatile
    internal var cachedAddress = ""

    @Volatile
    internal var cachedCountry = ""

    @Volatile
    internal var cachedPoiName = ""

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        startFrameworkConfigReceiver()
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val pkg = param.packageName
        val classLoader = param.classLoader
        handleLoadPackage(pkg, classLoader)
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        val loader = currentClassLoader ?: return false
        if (currentPackageName.isBlank()) return false
        param.setSavedInstanceState(hashMapOf<String, Any>(
            "host" to HotReloadHost(currentPackageName, loader).save(),
            "callbacks" to arrayOf(
                capturedLocationListeners.toTypedArray(), capturedAMapListeners.toTypedArray(),
                capturedBaiduListeners.toTypedArray(), capturedTencentListeners.toTypedArray(),
                capturedFusedLocationCallbacks.toTypedArray(), bleScanTimers.keys.toTypedArray()
            ),
            "sensor" to SensorStepHooker.saveReloadState()
        ))
        val worker = synchronized(pollingLock) {
            val previous = configWorker
            configWorker = null
            isConfigWorkerStarted = false
            previous?.interrupt()
            previous
        }
        worker?.join(2_000)
        if (worker?.isAlive == true) {
            startConfigWorker()
            return false
        }
        configReceiver?.close()
        configReceiver = null
        lastConfig = null
        nmeaListenerProxies.clear()
        bleScanTimers.values.forEach { it.cancel() }
        bleScanTimers.clear()
        SensorStepHooker.clearReloadState()
        capturedLocationListeners.clear()
        capturedAMapListeners.clear()
        capturedBaiduListeners.clear()
        capturedTencentListeners.clear()
        capturedFusedLocationCallbacks.clear()
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        val state = param.savedInstanceState as? Map<*, *>
        reinstallAfterHotReload(
            state = state?.get("host"),
            resolveHost = { resolveRunningHost(param) },
            removeOldHooks = { super.onHotReloaded(param) },
            install = { host ->
                currentPackageName = host.packageName
                currentClassLoader = host.classLoader
                startFrameworkConfigReceiver()
                handleLoadPackage(host.packageName, host.classLoader)
                val callbacks = state?.get("callbacks") as? Array<*>
                val lists = listOf(capturedLocationListeners, capturedAMapListeners,
                    capturedBaiduListeners, capturedTencentListeners, capturedFusedLocationCallbacks)
                lists.forEachIndexed { index, list ->
                    (callbacks?.getOrNull(index) as? Array<*>)?.filterNotNull()?.let { list.addAll(it) }
                }
                SensorStepHooker.restoreReloadState(state?.get("sensor"))
                (callbacks?.getOrNull(5) as? Array<*>)?.filterNotNull()?.forEach { callback ->
                    if (hasTypeByName(callback.javaClass, "android.bluetooth.BluetoothAdapter\$LeScanCallback")) {
                        startOldLeScanTimer(callback, host.classLoader)
                    } else startBleTimer(callback, host.classLoader)
                }
                android.util.Log.i("LocationSpoofer", "[HotReload] Reinstalled hooks in ${param.processName}")
            }
        )
    }

    private fun resolveRunningHost(param: XposedModuleInterface.HotReloadedParam): HotReloadHost? {
        if (param.isSystemServer) return null
        val app = runCatching {
            Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication")
                .invoke(null) as? android.app.Application
        }.getOrNull() ?: return null
        return HotReloadHost(app.packageName, app.classLoader)
    }

    companion object {
        internal const val VERBOSE_CELL_BUILD_LOGS = false

        internal fun primitiveDefault(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }

        fun hasTypeByName(clazz: Class<*>?, typeName: String): Boolean {
            if (clazz == null) return false
            if (clazz.name == typeName) return true
            for (iface in clazz.interfaces) {
                if (hasTypeByName(iface, typeName)) return true
            }
            return hasTypeByName(clazz.superclass, typeName)
        }
    }

    fun handleLoadPackage(pkg: String, classLoader: ClassLoader) {
        val processName = try {
            File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n')
        } catch (e: Exception) {
            pkg
        }
        val actualHostPkg = processName.substringBefore(":")
        if (actualHostPkg.isNotBlank() && !actualHostPkg.startsWith("android") && actualHostPkg != "system_server" && actualHostPkg != "system") {
            currentPackageName = actualHostPkg
        } else if (currentPackageName.isEmpty()) {
            currentPackageName = pkg
        }
        if (currentClassLoader == null || (pkg == currentPackageName)) {
            currentClassLoader = classLoader
        }

        startConfigWorker()
        if (isSystemProcess(pkg, processName)) return

        if (environmentHooksInstalled) {
            readConfig()
            return
        }
        environmentHooksInstalled = true

        XposedBridge.log("[LocationSpoofer] Hooking package: $pkg")
        android.util.Log.e(
            "LocationSpoofer",
            "[INJECTED] Hooking package: $pkg process=$processName"
        )
        XposedBridge.logOpenCellId("handleLoadPackage pkg=$pkg classLoader=$classLoader")

        hookAntiDetection(classLoader)

        hookLocationAPIs(classLoader, pkg)
        hookGnssStatus(classLoader)

        try {
            XposedHelpers.hookMethod(
                "android.app.Application",
                classLoader,
                "attachBaseContext",
                android.content.Context::class.java
            ) { chain, method ->
                val result = chain.proceed(chain.args.toTypedArray())
                val app = chain.thisObject as? android.app.Application
                val appCl = app?.classLoader
                XposedBridge.log("[LocationSpoofer] attachBaseContext fired, appClassLoader=$appCl sameAsInitial=${appCl === classLoader}")
                if (appCl != null) {
                    hookAllMapSdks(appCl)
                }
                return@hookMethod result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.app.Application",
                classLoader,
                "onCreate"
            ) { chain, method ->
                val result = chain.proceed(chain.args.toTypedArray())
                val app = chain.thisObject as? android.app.Application
                val appCl = app?.classLoader
                XposedBridge.log("[LocationSpoofer] Application.onCreate fired, appClassLoader=$appCl sameAsInitial=${appCl === classLoader}")
                if (appCl != null) {
                    hookAllMapSdks(appCl)
                }
                return@hookMethod result
            }
        } catch (_: Throwable) {}

        hookWifiEnvironment(classLoader)
        hookCellEnvironment(classLoader)
        hookConnectivityLayer(classLoader)
        hookBluetoothLE(classLoader)
        SensorStepHooker.hookSensorStepSimulation(classLoader)

        readConfig()
    }

    internal fun hookAllMapSdks(cl: ClassLoader) {
        try { hookAMapSDK(cl) } catch (_: Throwable) {}
        try { hookTencentSDK(cl) } catch (_: Throwable) {}
        try { hookBaiduSDK(cl) } catch (_: Throwable) {}
        try { hookGoogleFusedLocation(cl) } catch (_: Throwable) {}
    }

    private fun isSystemProcess(pkg: String, processName: String): Boolean =
        pkg == "android" || pkg == "system" ||
            processName == "system_server" || processName == "system" ||
            pkg == "com.android.systemui" || processName == "com.android.systemui" ||
            pkg == "com.android.phone" || processName == "com.android.phone" ||
            pkg == "com.android.bluetooth" || processName.startsWith("com.android.bluetooth")

    internal val rng = Random()
    internal var hookDriftLat = 0.0
    internal var hookDriftLng = 0.0
    internal var hookAccuracyDrift = 0.0
    internal var hookLastCallTime = 0L

    @Volatile
    internal var lastConfig: JSONObject? = null

    @Volatile
    internal var lastOpenCellConfigLogKey: String? = null

    @Volatile
    private var configReceiver: FrameworkConfigReceiver? = null
    @Volatile private var configWorker: Thread? = null
    @Volatile private var isConfigWorkerStarted = false
    private val pollingLock = Any()

    private fun startFrameworkConfigReceiver() {
        if (configReceiver != null) return
        configReceiver = FrameworkConfigReceiver(
            getPreferences = { getRemotePreferences(FrameworkConfigChannel.GROUP) },
            readFile = { name ->
                ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(name))
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            },
            onConfig = { snapshot ->
                val config = normalizeConfig(snapshot.config)
                lastConfig = config
                logOpenCellConfigLoaded(snapshot.source, config)
            },
            onError = { error ->
                XposedBridge.log("[Config] Framework receive failed: ${error.javaClass.simpleName}: ${error.message}")
            },
            readHead = {
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(openRemoteFile(FrameworkConfigChannel.HEAD_FILE))
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        ).also { it.start() }
    }

    internal fun logOpenCellConfigLoaded(source: String, config: JSONObject) {
        val cellArray = config.optJSONArray("cell_json")
        val cellCount = cellArray?.length() ?: 0
        val btArray = config.optJSONArray("bluetooth_json")
        val btCount = btArray?.length() ?: 0
        val active = config.optBoolean("active", false)
        val mockBt = config.optBoolean("mock_bluetooth", true)
        val lat = config.optDouble("lat", 0.0)
        val lng = config.optDouble("lng", 0.0)
        val logKey = "$active|$mockBt|$lat|$lng|$cellCount|$btCount"
        if (logKey != lastOpenCellConfigLogKey) {
            lastOpenCellConfigLogKey = logKey
            val msg = "[LocationSpoofer] 配置已加载[$source]: active=$active, pkg=$currentPackageName, lat=$lat, lng=$lng, 蓝牙数=$btCount, 基站数=$cellCount"
            android.util.Log.i("LocationSpoofer", msg)
            XposedBridge.log(msg)
        }
    }

    internal fun normalizeConfig(config: JSONObject): JSONObject {
        if (!config.has("wifi_json")) config.put("wifi_json", org.json.JSONArray())
        val gcj02Lat = config.optDouble("lat", 0.0)
        val gcj02Lng = config.optDouble("lng", 0.0)

        val bd09 = gcj02ToBd09(gcj02Lat, gcj02Lng)
        config.put("bd09_lat", bd09.first)
        config.put("bd09_lng", bd09.second)

        val wgs84 = gcj02ToWgs84(gcj02Lat, gcj02Lng)
        config.put("wgs84_lat", wgs84.first)
        config.put("wgs84_lng", wgs84.second)

        return config
    }

    internal fun readConfig(): JSONObject? = lastConfig

    private fun startConfigWorker() {
        if (!isConfigWorkerStarted) {
            synchronized(pollingLock) {
                if (!isConfigWorkerStarted) {
                    isConfigWorkerStarted = true

                    val worker = Thread {
                        val workerThread = Thread.currentThread()
                        while (configWorker === workerThread && !workerThread.isInterrupted) {
                            try {
                                Thread.sleep(1_000L)
                                val newConfig = readConfig()

                                if (newConfig != null && newConfig.optBoolean("active", false)) {
                                    cachedProvince = newConfig.optString("province", "")
                                    cachedCity = newConfig.optString("city", "")
                                    cachedDistrict = newConfig.optString("district", "")
                                    cachedStreet = newConfig.optString("street", "")
                                    cachedStreetNum = newConfig.optString("streetNum", "")
                                    cachedAddress = newConfig.optString("address", "")
                                    cachedCountry = newConfig.optString("country", "")
                                    cachedPoiName = newConfig.optString("poiName", "")

                                    val timeNow = System.currentTimeMillis()
                                    val elapsedNanos = android.os.SystemClock.elapsedRealtimeNanos()
                                    val motion = RouteEngine.calculateCurrentPosition(newConfig, timeNow)

                                    val basePkg = currentPackageName.substringBefore(":")
                                    if (basePkg == "com.vincenthzr.locationspoofer") {
                                        continue
                                    }

                                    val cl = currentClassLoader
                                    val nCount = capturedLocationListeners.size
                                    val aCount = capturedAMapListeners.size
                                    val bCount = capturedBaiduListeners.size
                                    val tCount = capturedTencentListeners.size
                                    val fCount = capturedFusedLocationCallbacks.size

                                    val isStationary = motion.speed <= 0.05
                                    val (targetLat, targetLng) = getAppTargetCoordinate(motion.lat, motion.lng, newConfig, "WGS-84")
                                    fun jittered(lat: Double, lng: Double) = if (isStationary) lat to lng else getJitteredLocation(lat, lng)
                                    val (pushLat, pushLng) = jittered(targetLat, targetLng)
                                    val pushSpeed = motion.speed
                                    val pushBearing = motion.bearing
                                    val pushAccuracy = if (isStationary) 2.5f else getJitteredAccuracy()
                                    val pushAltitude = RouteEngine.realisticAltitude(newConfig)

                                    val mainHandler = try {
                                        android.os.Handler(android.os.Looper.getMainLooper())
                                    } catch (_: Throwable) {
                                        null
                                    }

                                    val dispatchBlock = Runnable {
                                        if (configWorker !== workerThread) return@Runnable
                                        if (nCount > 0 && cl != null) {
                                            val listenersToNotify = capturedLocationListeners.toList()
                                            for (listener in listenersToNotify) {
                                                try {
                                                    val mockLoc = android.location.Location(android.location.LocationManager.GPS_PROVIDER)
                                                    applySpoofedFix(mockLoc, pushLat, pushLng, pushSpeed, pushBearing, pushAccuracy, newConfig, timeNow, elapsedNanos)
                                                    var called = false
                                                    try {
                                                        XposedHelpers.callMethod(
                                                            listener,
                                                            "onLocationChanged",
                                                            mockLoc
                                                        )
                                                        called = true
                                                    } catch (_: Throwable) {}
                                                    if (!called) {
                                                        try {
                                                            XposedHelpers.callMethod(
                                                                listener,
                                                                "accept",
                                                                mockLoc
                                                            )
                                                            called = true
                                                        } catch (_: Throwable) {}
                                                    }
                                                    if (!called) {
                                                        try {
                                                            XposedHelpers.callMethod(
                                                                listener,
                                                                "onLocationChanged",
                                                                listOf(mockLoc)
                                                            )
                                                            called = true
                                                        } catch (_: Throwable) {}
                                                    }
                                                } catch (_: Throwable) {
                                                }
                                            }
                                        }

                                        if (fCount > 0 && cl != null) {
                                            val callbacksToNotify = capturedFusedLocationCallbacks.toList()
                                            for (callback in callbacksToNotify) {
                                                try {
                                                    val callbackCl = callback.javaClass.classLoader ?: cl
                                                    val mockLoc = android.location.Location(android.location.LocationManager.GPS_PROVIDER)
                                                    applySpoofedFix(mockLoc, pushLat, pushLng, pushSpeed, pushBearing, pushAccuracy, newConfig, timeNow, elapsedNanos)

                                                    val locationResultClass = Class.forName(
                                                        "com.google.android.gms.location.LocationResult",
                                                        false,
                                                        callbackCl
                                                    )
                                                    val locationsList = java.util.Collections.singletonList(mockLoc)
                                                    val result = XposedHelpers.callStaticMethod(
                                                        locationResultClass,
                                                        "create",
                                                        locationsList
                                                    )
                                                    XposedHelpers.callMethod(callback, "onLocationResult", result)
                                                } catch (_: Throwable) {
                                                }
                                            }
                                        }

                                        if (aCount > 0 && cl != null) {
                                            val (aMapLat, aMapLng) = getAppTargetCoordinate(motion.lat, motion.lng, newConfig, "GCJ-02")
                                            val (aMapPushLat, aMapPushLng) = jittered(aMapLat, aMapLng)
                                            val listenersToNotify = capturedAMapListeners.toList()
                                            for (listener in listenersToNotify) {
                                                try {
                                                    val listenerCl = listener.javaClass.classLoader ?: cl
                                                    val amapLocationClass = Class.forName(
                                                        "com.amap.api.location.AMapLocation",
                                                        false,
                                                        listenerCl
                                                    )
                                                    val mockAMapLoc =
                                                        amapLocationClass.getConstructor(String::class.java)
                                                            .newInstance("gps")
                                                    XposedHelpers.callMethod(
                                                        mockAMapLoc,
                                                        "setLatitude",
                                                        aMapPushLat
                                                    )
                                                    XposedHelpers.callMethod(
                                                        mockAMapLoc,
                                                        "setLongitude",
                                                        aMapPushLng
                                                    )
                                                    XposedHelpers.callMethod(
                                                        mockAMapLoc,
                                                        "setAccuracy",
                                                        pushAccuracy
                                                    )
                                                    XposedHelpers.callMethod(
                                                        mockAMapLoc,
                                                        "setSpeed",
                                                        pushSpeed
                                                    )
                                                    XposedHelpers.callMethod(
                                                        mockAMapLoc,
                                                        "setBearing",
                                                        pushBearing
                                                    )
                                                    XposedHelpers.callMethod(
                                                        mockAMapLoc,
                                                        "setAltitude",
                                                        pushAltitude
                                                    )
                                                    XposedHelpers.callMethod(
                                                        mockAMapLoc,
                                                        "setTime",
                                                        timeNow
                                                    )
                                                    try {
                                                        XposedHelpers.callMethod(mockAMapLoc, "setSatellites", newConfig.optInt("satellite_count", 20))
                                                        XposedHelpers.callMethod(mockAMapLoc, "setGpsAccuracyStatus", 1)
                                                        XposedHelpers.callMethod(mockAMapLoc, "setLocationType", 1)
                                                    } catch (_: Throwable) {}
                                                    try {
                                                        val extras = android.os.Bundle().also { putSatelliteExtras(it, newConfig.optInt("satellite_count", 20)) }
                                                        XposedHelpers.callMethod(mockAMapLoc, "setExtras", extras)
                                                    } catch (_: Throwable) {}
                                                    try {
                                                        XposedHelpers.callMethod(
                                                            mockAMapLoc,
                                                            "setElapsedRealtimeNanos",
                                                            elapsedNanos
                                                        )
                                                    } catch (_: Throwable) {
                                                    }
                                                    try {
                                                        XposedHelpers.callMethod(
                                                            mockAMapLoc,
                                                            "setIsFromMockProvider",
                                                            false
                                                        )
                                                    } catch (_: Throwable) {
                                                    }
                                                    XposedHelpers.callMethod(
                                                        listener,
                                                        "onLocationChanged",
                                                        mockAMapLoc
                                                    )
                                                } catch (_: Throwable) {
                                                }
                                            }
                                        }

                                        if (bCount > 0 && cl != null) {
                                            val (baiduLat, baiduLng) = getAppTargetCoordinate(motion.lat, motion.lng, newConfig, "BD-09")
                                            val (baiduPushLat, baiduPushLng) = jittered(baiduLat, baiduLng)
                                            val listenersToNotify = capturedBaiduListeners.toList()
                                            for (listener in listenersToNotify) {
                                                try {
                                                    val listenerCl = listener.javaClass.classLoader ?: cl
                                                    val bdLocationClass = Class.forName(
                                                        "com.baidu.location.BDLocation",
                                                        false,
                                                        listenerCl
                                                    )
                                                    val mockBDLoc =
                                                        bdLocationClass.getConstructor().newInstance()
                                                    XposedHelpers.callMethod(
                                                        mockBDLoc,
                                                        "setLatitude",
                                                        baiduPushLat
                                                    )
                                                    XposedHelpers.callMethod(
                                                        mockBDLoc,
                                                        "setLongitude",
                                                        baiduPushLng
                                                    )
                                                    try { XposedHelpers.setDoubleField(mockBDLoc, "mLatitude", baiduPushLat) } catch (_: Throwable) {}
                                                    try { XposedHelpers.setDoubleField(mockBDLoc, "mLongitude", baiduPushLng) } catch (_: Throwable) {}
                                                    try { XposedHelpers.callMethod(mockBDLoc, "setCoorType", "bd09ll") } catch (_: Throwable) {}
                                                    try { XposedHelpers.setObjectField(mockBDLoc, "mCoorType", "bd09ll") } catch (_: Throwable) {}
                                                    XposedHelpers.callMethod(mockBDLoc, "setRadius", pushAccuracy)
                                                    XposedHelpers.callMethod(mockBDLoc, "setSpeed", pushSpeed * 3.6f)
                                                    XposedHelpers.callMethod(mockBDLoc, "setDirection", pushBearing)
                                                    XposedHelpers.callMethod(mockBDLoc, "setLocType", 61)
                                                    XposedHelpers.callMethod(mockBDLoc, "setSatelliteNumber", newConfig.optInt("satellite_count", 20))
                                                    XposedHelpers.callMethod(mockBDLoc, "setGpsCheckStatus", 1)
                                                    try { XposedHelpers.callMethod(mockBDLoc, "setMockGps", 0) } catch (_: Throwable) {}
                                                    try { XposedHelpers.callMethod(mockBDLoc, "setTime", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())) } catch (_: Throwable) {}
                                                    XposedHelpers.callMethod(
                                                        listener,
                                                        "onReceiveLocation",
                                                        mockBDLoc
                                                    )
                                                } catch (_: Throwable) {
                                                }
                                            }
                                        }

                                        if (tCount > 0 && cl != null) {
                                            try {
                                                val (tencentLat, tencentLng) = getAppTargetCoordinate(motion.lat, motion.lng, newConfig, "GCJ-02")
                                                val (tencentPushLat, tencentPushLng) = jittered(tencentLat, tencentLng)
                                                val tencentLocInterface = Class.forName(
                                                    "com.tencent.map.geolocation.TencentLocation",
                                                    false,
                                                    cl
                                                )
                                                val proxyLoc = Proxy.newProxyInstance(
                                                    cl, arrayOf(tencentLocInterface)
                                                ) { proxy, method, args ->
                                                    when (method.name) {
                                                        "getLatitude" -> tencentPushLat
                                                        "getLongitude" -> tencentPushLng
                                                        "getAltitude" -> pushAltitude
                                                        "getProvider" -> "gps"
                                                        "getAccuracy" -> pushAccuracy
                                                        "getSpeed" -> pushSpeed
                                                        "getBearing" -> pushBearing
                                                        "getTime" -> timeNow
                                                        "hashCode" -> System.identityHashCode(proxy)
                                                        "equals" -> proxy === args?.getOrNull(0)
                                                        "toString" -> "TencentLocation($tencentPushLat,$tencentPushLng)"
                                                        else -> primitiveDefault(method.returnType)
                                                    }
                                                }
                                                val listenersToNotify = capturedTencentListeners.toList()
                                                for (listener in listenersToNotify) {
                                                    try {
                                                        XposedHelpers.callMethod(
                                                            listener,
                                                            "onLocationChanged",
                                                            proxyLoc,
                                                            0,
                                                            "ok"
                                                        )
                                                    } catch (_: Throwable) {
                                                    }
                                                }
                                            } catch (_: Throwable) {
                                            }
                                        }

                                        if (cl != null) {
                                            SensorStepHooker.dispatchStepEvents(newConfig, cl)
                                        }
                                    }

                                    if (mainHandler != null) {
                                        mainHandler.post(dispatchBlock)
                                    } else {
                                        dispatchBlock.run()
                                    }
                                }
                            } catch (_: InterruptedException) {
                                break
                            } catch (t: Throwable) {
                                XposedBridge.log("ConfigWorker error: " + t.javaClass.simpleName + ": " + t.message)
                            }
                        }
                    }.apply {
                        isDaemon = true
                        name = "LocationSpoofer_MotionWorker"
                    }
                    configWorker = worker
                    worker.start()
                }
            }
        }
    }
}
