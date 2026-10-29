package io.github.amahteru.locationspoofer.xposed

import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import io.github.amahteru.locationspoofer.xposed.hooks.network.*
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.VendorRegistry
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemComponent
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemProcess
import io.github.amahteru.locationspoofer.xposed.diagnostics.HookStatus

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONObject
import java.io.File
import android.os.ParcelFileDescriptor
import io.github.amahteru.locationspoofer.utils.FrameworkConfigChannel
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

class LocationHooker : XposedModule() {
    init {
        XposedHelpers.module = this
        XposedHelpers.hookReporter = object : HookReporter {
            override fun methodHooked(className: String, methodName: String, count: Int) =
                HookStatus.methodHooked(className, methodName, count)
            override fun error(name: String, t: Throwable) = HookStatus.error(name, t)
        }
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

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        XposedBridge.log("[SysHook] onSystemServerStarting invoked! system_server classLoader=${param.classLoader}")
        handleLoadPackage("android", param.classLoader)
    }

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
                XposedBridge.log("[HotReload] Reinstalled hooks in ${param.processName}; generation=${HookStatus.generationId}")
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
        handleSystemProcess(pkg, processName, classLoader)
    }

    private fun handleSystemProcess(pkg: String, processName: String, classLoader: ClassLoader): Boolean {
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

        XposedBridge.log("[SysHook] Core system process detected: pkg=$pkg, process=$processName, myUid=${android.os.Process.myUid()}")
        if (systemHooksInstalled) {
            readConfig()
            return true
        }
        systemHooksInstalled = true
        XposedBridge.log("[SysHook] Deploying system framework hooks in $processName...")

        val earlyCfg = readConfig()
        VendorRegistry.applyManualOverride(earlyCfg?.optString("vendor_override", "auto"))

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

        VendorRegistry.logSelectionOnce()
        deploy("installExtraHooks") { VendorRegistry.installExtraHooks(this, classLoader) }

        val cfg = readConfig()
        XposedBridge.log("[SysHook] System framework hooks deployed in $processName. Config active=${cfg?.optBoolean("active")}, globalMode=${cfg?.optBoolean("system_hook_global_mode")}, targetPkgs=${cfg?.optJSONArray("system_hook_packages")?.length() ?: 0}")
        return true
    }

    internal fun dumpSystemServiceInternals(classLoader: ClassLoader) {
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
                HookStatus.configLoaded(snapshot.source, snapshot.publishedAt)
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
        val isGlobal = config.optBoolean("system_hook_global_mode", false)
        val sysPkgs = config.optJSONArray("system_hook_packages")?.length() ?: 0
        val logKey = "$active|$mockBt|$lat|$lng|$cellCount|$btCount|$isGlobal|$sysPkgs"
        if (logKey != lastOpenCellConfigLogKey) {
            lastOpenCellConfigLogKey = logKey
            val msg = "[SysHook] 配置已加载[$source]: active=$active, globalMode=$isGlobal, 目标应用数=$sysPkgs, lat=$lat, lng=$lng, 蓝牙数=$btCount, 基站数=$cellCount"
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
