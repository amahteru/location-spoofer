package com.vincenthzr.locationspoofer.xposed

import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import com.vincenthzr.locationspoofer.xposed.hooks.network.*
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.VendorRegistry
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemComponent
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemProcess
import com.vincenthzr.locationspoofer.xposed.diagnostics.HookStatus

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONObject
import java.io.File
import android.os.ParcelFileDescriptor
import com.vincenthzr.locationspoofer.utils.FrameworkConfigChannel
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

class LocationHooker : XposedModule() {
    init {
        XposedHelpers.module = this
    }

    internal val hookedCallbackClasses = ConcurrentHashMap<Class<*>, Boolean>()

    @Volatile
    internal var systemHooksInstalled = false

    @Volatile
    internal var isSystemServerProcess = false

    @Volatile
    internal var isPhoneProcessInstance = false

    @Volatile
    internal var isBluetoothProcessInstance = false

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

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val pkg = param.packageName
        val classLoader = param.classLoader
        handleLoadPackage(pkg, classLoader)
    }

    // LibXposed API 101/102: 系统服务专属入口
    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        android.util.Log.e(
            "LocationSpoofer",
            "[SysHook] onSystemServerStarting invoked! system_server classLoader=${param.classLoader}"
        )
        handleLoadPackage("android", param.classLoader)
    }

    // LibXposed API 102: 热重载请求前置确认与资源清理
    internal val vendorExtraHooks = mutableListOf<AutoCloseable>()

    @Volatile internal var retiringGeneration = false
    private val ownedReloadTimers = java.util.Collections.synchronizedSet(mutableSetOf<java.util.Timer>())

    internal fun ownReloadTimer(timer: java.util.Timer): java.util.Timer {
        synchronized(ownedReloadTimers) {
            if (retiringGeneration) timer.cancel() else ownedReloadTimers.add(timer)
        }
        return timer
    }

    internal fun releaseReloadTimer(timer: java.util.Timer) { ownedReloadTimers.remove(timer) }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        val loader = currentClassLoader ?: return false
        if (currentPackageName.isBlank()) return false
        // A new module instance receives this bootstrap-owned state. Never pass this module or
        // module-defined registration records across the classloader boundary.
        param.setSavedInstanceState(hashMapOf<String, Any>(
            "host" to HotReloadHost(currentPackageName, loader).save(),
            "system_location" to saveSystemLocationReloadState(),
            "system_ble" to saveSystemBleReloadState()
        ))
        retiringGeneration = true
        val worker = synchronized(pollingLock) {
            val previous = configWorker
            configWorker = null
            isConfigWorkerStarted = false
            previous?.interrupt()
            previous
        }
        worker?.join(2_000)
        if (worker?.isAlive == true) {
            retiringGeneration = false
            startConfigWorker()
            return false
        }
        configReceiver?.close()
        configReceiver = null
        lastConfig = null
        synchronized(ownedReloadTimers) {
            ownedReloadTimers.forEach { it.cancel() }
            ownedReloadTimers.clear()
        }
        vendorExtraHooks.asReversed().forEach { runCatching { it.close() } }
        vendorExtraHooks.clear()
        ModuleBinderDeaths.close()
        HookStatus.close()
        clearSystemLocationReloadState()
        clearSystemBleReloadState()
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        val state = param.savedInstanceState as? Map<*, *>
        reinstallAfterHotReload(
            state = state?.get("host"),
            // First upgrade from older versions has no saved state. Recover the already-running
            // host through its Application / system service, not module-instance fields.
            resolveHost = { resolveRunningHost(param) },
            removeOldHooks = { super.onHotReloaded(param) },
            install = { host ->
                currentPackageName = host.packageName
                currentClassLoader = host.classLoader
                HookStatus.markHotReload()
                startFrameworkConfigReceiver()
                handleLoadPackage(host.packageName, host.classLoader)
                restoreSystemLocationReloadState(state?.get("system_location"))
                restoreSystemBleReloadState(state?.get("system_ble"))
                android.util.Log.i("LocationSpoofer", "[HotReload] Reinstalled hooks in ${param.processName}; generation=${HookStatus.generationId}")
            }
        )
    }

    private fun resolveRunningHost(param: XposedModuleInterface.HotReloadedParam): HotReloadHost? {
        val app = runCatching {
            Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication")
                .invoke(null) as? android.app.Application
        }.getOrNull()
        if (!param.isSystemServer && app != null) return HotReloadHost(app.packageName, app.classLoader)
        if (param.isSystemServer) {
            // Hook handles belong to the framework; their declaring classes retain the real
            // system-service loader even when an older module did not save lifecycle state.
            // 热重载回调本身只在 API 102 框架上出现，这里的判断只是把这一前提写明
            if (apiVersion >= io.github.libxposed.api.XposedInterface.API_102) param.oldHookHandles.forEach { handle ->
                val clazz = handle.executable.declaringClass
                if (clazz.name.startsWith("com.android.server.")) {
                    val loader = clazz.classLoader
                    if (loader != null) return HotReloadHost("android", loader)
                }
            }
            val serviceLoader = runCatching {
                val serviceManager = Class.forName("android.os.ServiceManager")
                serviceManager.getDeclaredMethod("getService", String::class.java).invoke(null, "location")
                    ?.javaClass?.classLoader
            }.getOrNull()
            val candidates = listOfNotNull(serviceLoader, Thread.currentThread().contextClassLoader,
                ClassLoader.getSystemClassLoader(), javaClass.classLoader?.parent)
            candidates.forEach { candidate ->
                val hostLoader = runCatching { candidate.loadClass("com.android.server.SystemServer").classLoader }.getOrNull()
                if (hostLoader != null) return HotReloadHost("android", hostLoader)
            }
        }
        return null
    }


    companion object {

        internal const val VERBOSE_CELL_BUILD_LOGS = false

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
        // 模块作用域只有 system / com.android.phone / com.android.bluetooth 三个系统进程，
        // 所有模拟都在系统服务里完成，不向任何 App 进程注入
        handleSystemProcess(pkg, processName, classLoader)
    }

    /**
     * 在 system_server / com.android.phone / com.android.bluetooth 里安装系统服务级 Hook，一次性对全设备生效。
     * @return true 表示该进程是核心系统进程并已处理完毕
     */
    private fun handleSystemProcess(pkg: String, processName: String, classLoader: ClassLoader): Boolean {
        // 防止注入到 SystemUI 导致崩溃或无意义占用
        if (pkg == "com.android.systemui" || processName == "com.android.systemui") {
            return true
        }

        val isSystemServer =
            (processName == "system_server") || (processName == "system")
        if (!isSystemServer && (pkg == "android" || pkg == "system")) return true
        val isPhoneProcess =
            (pkg == "com.android.phone") || (processName == "com.android.phone")
        val isBluetoothProcess =
            (pkg == "com.android.bluetooth") || (processName.startsWith("com.android.bluetooth"))

        if (isSystemServer) isSystemServerProcess = true
        if (isPhoneProcess) isPhoneProcessInstance = true
        if (isBluetoothProcess) isBluetoothProcessInstance = true

        val isCoreSystemProcess = isSystemServer || isPhoneProcess || isBluetoothProcess
        if (!isCoreSystemProcess) return false

        android.util.Log.e(
            "LocationSpoofer",
            "[SysHook] Core system process detected: pkg=$pkg, process=$processName, myUid=${android.os.Process.myUid()}"
        )
        if (systemHooksInstalled) {
            readConfig()
            return true
        }
        systemHooksInstalled = true
        XposedBridge.log("[SysHook] Deploying system framework hooks in $processName...")

        // 厂商适配方案的手动覆盖（VendorRegistry.applyManualOverride）必须在下面任何一个
        // hookSystemXxxService 之前生效——它们会通过 SystemClassLocator 查询 VendorRegistry，从而触发
        // VendorRegistry.active 这个 by lazy 属性首次求值，晚了就再也覆盖不了。这里提前读一次
        // 配置：readConfig() 可重复调用，后续调用只返回内存里已缓存的 lastConfig，只读内存。
        val earlyCfg = readConfig()
        VendorRegistry.applyManualOverride(earlyCfg?.optString("vendor_override", "auto"))

        // Hook 运行状态报告（App 的"系统适配 → Hook 运行状态"页读取），每个系统进程各写一份
        HookStatus.begin(
            when {
                isSystemServer -> SystemProcess.SYSTEM_SERVER
                isPhoneProcess -> SystemProcess.PHONE
                else -> SystemProcess.BLUETOOTH
            }
        )
        fun deploy(name: String, block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                XposedBridge.log("[SysHook] $name error: $t")
                HookStatus.error(name, t)
            }
        }

        if (isSystemServer) {
            // 只读侦察：把各组件候选类的方法 / 字段列表打进日志，适配新系统时才需要，由配置里的调试开关控制
            if (earlyCfg?.optBoolean("debug_dump_system_services", false) == true) {
                deploy("dumpSystemServiceInternals") { dumpSystemServiceInternals(classLoader) }
            }
            deploy("hookSystemLocationService") { hookSystemLocationService(classLoader) }
            deploy("hookSystemWifiService") { hookSystemWifiService(classLoader) }
            deploy("hookSystemConnectivityService") { hookSystemConnectivityService(classLoader) }
            deploy("hookSystemTelephonyRegistry") { hookSystemTelephonyRegistry(classLoader) }
            deploy("hookSystemAppOpsService") { hookSystemAppOpsService(classLoader) }
        }

        if (isPhoneProcess) {
            deploy("hookSystemTelephonyService") { hookSystemTelephonyService(classLoader) }
        }

        if (isBluetoothProcess) {
            deploy("hookSystemBluetoothService") { hookSystemBluetoothService(classLoader) }
        }

        // 机型/系统适配层：先打印一次命中的适配器画像，再执行该机型专属的额外 Hook（默认空实现，
        // 各厂商在 vendor/profiles/ 下按需覆写）。基线 Hook 已在上面装完，这里是纯追加，不影响任何机型。
        VendorRegistry.logSelectionOnce()
        deploy("installExtraHooks") { VendorRegistry.installExtraHooks(this, classLoader) }

        val cfg = readConfig()
        android.util.Log.e(
            "LocationSpoofer",
            "[SysHook] System framework hooks deployed in $processName. Config active=${cfg?.optBoolean("active")}, globalMode=${cfg?.optBoolean("system_hook_global_mode")}, targetPkgs=${cfg?.optJSONArray("system_hook_packages")?.length() ?: 0}"
        )
        return true
    }

    /**
     * 只读侦察，不装任何会改变行为的 Hook：探索"直接改 system_server 内部服务实现，
     * 一次性对全设备生效"这个方向时，第一步必须先摸清楚这台设备/这个安卓版本实际的
     * 内部类结构长什么样——LocationManagerService/WifiServiceImpl 这些类不是公开 API，
     * 字段和方法名在不同安卓版本、不同 OEM 定制 ROM 之间差异很大，且没有公开文档，
     * 瞎猜字段名直接上 Hook 是 system_server 崩溃、设备进重启循环最常见的原因。
     * 这里只用反射枚举候选类的方法名/字段名打进日志，不拦截、不修改任何返回值，
     * 单个类枚举失败也只是记一条日志，不会影响 system_server 本身的运行。
     */
    internal fun dumpSystemServiceInternals(classLoader: ClassLoader) {
        // 各组件的候选类名（含当前适配器给出的定制候选），再加上两个只用于排查的 GNSS 内部类
        val candidateClassNames = SystemComponent.entries
            .filter { it.process == SystemProcess.SYSTEM_SERVER }
            .flatMap { VendorRegistry.classCandidates(it) } + listOf(
            "com.android.server.location.gnss.GnssLocationProvider",
            "com.android.server.location.gnss.GnssManagerService",
        )
        for (className in candidateClassNames) {
            try {
                val clazz = classLoader.loadClass(className)
                val methodNames = clazz.declaredMethods.map { it.name }.distinct().sorted()
                val fieldInfo = clazz.declaredFields.map { "${it.type.simpleName} ${it.name}" }.sorted()
                XposedBridge.log(
                    "[LocationSpoofer][SysDump] FOUND $className (superclass=${clazz.superclass?.name})\n" +
                        "  methods(${methodNames.size})=${methodNames.joinToString(",")}\n" +
                        "  fields(${fieldInfo.size})=${fieldInfo.joinToString(",")}"
                )
            } catch (e: ClassNotFoundException) {
                XposedBridge.log("[LocationSpoofer][SysDump] not found: $className")
            } catch (e: Throwable) {
                XposedBridge.log("[LocationSpoofer][SysDump] error dumping $className: $e")
            }
        }
    }

    // 坐标抖动与精度起伏的随机过程状态，见 CoordinateConverter 的 getJitteredLocation / getJitteredAccuracy
    internal val rng = Random()
    internal var hookDriftLat = 0.0
    internal var hookDriftLng = 0.0
    internal var hookAccuracyDrift = 0.0
    internal var hookLastCallTime = 0L

    /** Complete normalized snapshots are swapped atomically by the framework receiver. */
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
                HookStatus.configLoaded(snapshot.source, snapshot.publishedAt)
                logOpenCellConfigLoaded(snapshot.source, config)
            },
            onError = { error ->
                XposedBridge.log("[Config] Framework receive failed: ${error.javaClass.simpleName}: ${error.message}")
            },
            readHead = {
                // Absent until an app version that writes it publishes once.
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
        val isGlobal = config.optBoolean("system_hook_global_mode", false)
        val sysPkgs = config.optJSONArray("system_hook_packages")?.length() ?: 0
        val logKey = "$active|$mockBt|$lat|$lng|$cellCount|$btCount|$isGlobal|$sysPkgs"
        if (logKey != lastOpenCellConfigLogKey) {
            lastOpenCellConfigLogKey = logKey
            val msg = "[SysHook] 配置已加载[$source]: active=$active, globalMode=$isGlobal, 目标应用数=$sysPkgs, lat=$lat, lng=$lng, 蓝牙数=$btCount, 基站数=$cellCount"
            android.util.Log.i("LocationSpoofer", msg)
            XposedBridge.log(msg)
        }
    }

    internal fun normalizeConfig(config: JSONObject): JSONObject {
        if (!config.has("wifi_json")) config.put("wifi_json", org.json.JSONArray())
        // UI 使用高德地图(AMapSDK)，cameraPosition.target 返回的是 GCJ-02 坐标系。
        // 因此 config["lat"/"lng"] 已经是 GCJ-02，不需要再做 BD-09 → GCJ-02 转换。
        val gcj02Lat = config.optDouble("lat", 0.0)
        val gcj02Lng = config.optDouble("lng", 0.0)

        // 派生出 BD-09（百度定位 SDK 需要）
        val bd09 = gcj02ToBd09(gcj02Lat, gcj02Lng)
        config.put("bd09_lat", bd09.first)
        config.put("bd09_lng", bd09.second)

        // 派生出 WGS-84（标准 Android Location 对象的理论坐标系）
        val wgs84 = gcj02ToWgs84(gcj02Lat, gcj02Lng)
        config.put("wgs84_lat", wgs84.first)
        config.put("wgs84_lng", wgs84.second)

        // lat/lng 保持 GCJ-02 不变，作为默认坐标（高德、腾讯等直接使用）
        return config
    }

    /** The hook call path performs no framework calls, parsing, or file IO. */
    internal fun readConfig(): JSONObject? = lastConfig

    /** Service discovery and the cached address need a timer independently of config reception. */
    private fun startConfigWorker() {
        if (!isConfigWorkerStarted) {
            synchronized(pollingLock) {
                if (!isConfigWorkerStarted) {
                    isConfigWorkerStarted = true

                    // 后台定时器只读内存中的配置，不轮询配置文件
                    val worker = Thread {
                        val workerThread = Thread.currentThread()
                        while (configWorker === workerThread && !workerThread.isInterrupted) {
                            try {
                                Thread.sleep(1_000L)
                                val newConfig = readConfig()

                                // 若系统核心服务在初次加载时尚未初始化完成，在后台轮询线程中重试挂载，直至成功
                                if (isSystemServerProcess && !isWifiServiceHooked && currentClassLoader != null) {
                                    try { hookSystemWifiService(currentClassLoader!!) } catch (t: Throwable) { XposedBridge.log("[SysWifi] Poller hookSystemWifiService error: $t") }
                                }
                                if (isPhoneProcessInstance && !isTelephonyServiceHooked && currentClassLoader != null) {
                                    try { hookSystemTelephonyService(currentClassLoader!!) } catch (t: Throwable) { XposedBridge.log("[SysCell] Poller hookSystemTelephonyService error: $t") }
                                }
                                if (isBluetoothProcessInstance && !isBluetoothServiceHooked && currentClassLoader != null) {
                                    try { hookSystemBluetoothService(currentClassLoader!!) } catch (t: Throwable) { XposedBridge.log("[SysBle] Poller hookSystemBluetoothService error: $t") }
                                }

                                if (newConfig != null && newConfig.optBoolean("active", false)) {
                                    // 直接从前端控制台下发的 JSON 配置中读取逆地理编码信息，0网络延迟，0硬编码
                                    cachedProvince = newConfig.optString("province", "")
                                    cachedCity = newConfig.optString("city", "")
                                    cachedDistrict = newConfig.optString("district", "")
                                    cachedStreet = newConfig.optString("street", "")
                                    cachedStreetNum = newConfig.optString("streetNum", "")
                                    cachedAddress = newConfig.optString("address", "")
                                    cachedCountry = newConfig.optString("country", "")
                                    cachedPoiName = newConfig.optString("poiName", "")
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
