package io.github.amahteru.locationspoofer.xposed.hooks

import android.os.IBinder
import android.os.IInterface
import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.hooks.network.*
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemComponent
import io.github.amahteru.locationspoofer.xposed.diagnostics.HookStatus
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemClassLocator
import io.github.amahteru.locationspoofer.xposed.utils.*
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

private fun sysLog(msg: String) = XposedBridge.log(msg)

private data class BleScannerRegistration(
    val callbackRef: WeakReference<Any>,
    val packageName: String,
    val registeredTime: Long = System.currentTimeMillis()
)

private val activeBleScanners = ConcurrentHashMap<IBinder, BleScannerRegistration>()

@Volatile
private var isBleTimerStarted = false

@Volatile
internal var isBluetoothServiceHooked = false

internal fun saveSystemBleReloadState(): Array<Any> = activeBleScanners.mapNotNull { (binder, info) ->
    info.callbackRef.get()?.let { callback -> arrayOf<Any>(binder, callback, info.packageName, info.registeredTime) }
}.toTypedArray()

internal fun clearSystemBleReloadState() {
    isBleTimerStarted = false
    activeBleScanners.clear()
}

internal fun LocationHooker.restoreSystemBleReloadState(value: Any?) {
    (value as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach { row ->
        val binder = row[0] as IBinder
        if (!binder.isBinderAlive) return@forEach
        val callback = row[1]!!
        activeBleScanners[binder] = BleScannerRegistration(WeakReference(callback), row[2] as String, row[3] as Long)
        ensureScannerCallbackHooked(callback)
        runCatching { ModuleBinderDeaths.watch(binder) { activeBleScanners.remove(binder) } }
    }
}

internal fun LocationHooker.hookSystemBluetoothService(classLoader: ClassLoader) {
    if (isBluetoothServiceHooked) return
    if (io.github.amahteru.locationspoofer.xposed.hooks.vendor.VendorRegistry.active.usesFrameworkBleDelivery) return

    var scanServiceClass = SystemClassLocator.locate(
        SystemComponent.BLUETOOTH_SCAN_SERVICE, classLoader,
        apexJars = listOf(
            "/apex/com.android.btservices/javalib/service-bluetooth.jar",
            "/apex/com.android.bluetooth/javalib/service-bluetooth.jar"
        )
    )

    if (scanServiceClass == null) {
        try {
            val smClass = XposedHelpers.findClassIfExists("android.os.ServiceManager", classLoader)
            val binder = if (smClass != null) {
                XposedHelpers.callStaticMethod(smClass, "getService", "bluetooth_gatt") as? android.os.IBinder
            } else null
            if (binder != null) {
                scanServiceClass = binder.javaClass
                HookStatus.classFound(SystemComponent.BLUETOOTH_SCAN_SERVICE, binder.javaClass, "ServiceManager.getService")
                sysLog("[SysBle] Captured GattService from ServiceManager.getService(\"bluetooth_gatt\"): ${binder.javaClass}")
            }
        } catch (_: Throwable) {}
    }

    if (scanServiceClass == null) {
        sysLog("[SysBle] BLE scan service not found in com.android.bluetooth")
        return
    }

    if (hookedCallbackClasses.putIfAbsent(scanServiceClass, true) != null) {
        isBluetoothServiceHooked = true
        return
    }

    startSystemBleHeartbeat(classLoader)

    hookScannerEntry(scanServiceClass, "registerScanner", classLoader, dispatchDelayMs = null)
    hookScannerEntry(scanServiceClass, "startScan", classLoader, dispatchDelayMs = 0L, register = false)
    hookScannerEntry(scanServiceClass, "registerAndStartScan", classLoader, dispatchDelayMs = 500L)

    isBluetoothServiceHooked = true
}

private fun LocationHooker.hookScannerEntry(
    serviceClass: Class<*>,
    methodName: String,
    classLoader: ClassLoader,
    dispatchDelayMs: Long?,
    register: Boolean = true
) {
    try {
        XposedHelpers.hookAllMethods(serviceClass, methodName) { chain, _ ->
            try {
                val config = readConfig()
                val explicitPkg = config?.let { SystemHookUtils.extractPackageName(chain.args) }
                if (config != null && SystemHookUtils.isTargetCaller(chain.thisObject, explicitPkg, config)) {
                    if (register) registerTargetScanner(chain.args, explicitPkg)
                    val mocking = config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)
                    if (dispatchDelayMs != null && mocking) {
                        sysLog("[SysBle] Target app started BLE scan via $methodName: ${explicitPkg ?: "caller"}")
                        if (dispatchDelayMs == 0L) {
                            dispatchFakeBeaconsToActiveScanners(config, classLoader)
                        } else {
                            val delayed = ownReloadTimer(Timer("LocationSpoofer-SysBleFirst", true))
                            delayed.schedule(object : TimerTask() {
                                override fun run() {
                                    try {
                                        dispatchFakeBeaconsToActiveScanners(readConfig() ?: return, classLoader)
                                    } catch (_: Throwable) {} finally {
                                        delayed.cancel()
                                        releaseReloadTimer(delayed)
                                    }
                                }
                            }, dispatchDelayMs)
                        }
                    }
                }
            } catch (e: Throwable) {
                sysLog("[SysBle] $methodName pre-hook error: $e")
            }
            return@hookAllMethods chain.proceed(chain.args.toTypedArray())
        }
    } catch (e: Throwable) {
        sysLog("[SysBle] hook $methodName failed: $e")
    }
}

private fun LocationHooker.registerTargetScanner(args: List<Any?>, explicitPkg: String?) {
    val callback = args.firstOrNull { arg ->
        arg != null && (LocationHooker.hasTypeByName(arg.javaClass, "android.bluetooth.le.IScannerCallback") ||
            arg.javaClass.name.contains("IScannerCallback"))
    } ?: return
    val binder = (callback as? IInterface)?.asBinder() ?: (callback as? IBinder) ?: return
    val pkgName = explicitPkg ?: "unknown"
    activeBleScanners[binder] = BleScannerRegistration(callbackRef = WeakReference(callback), packageName = pkgName)
    try {
        ModuleBinderDeaths.watch(binder) {
            activeBleScanners.remove(binder)
            sysLog("[SysBle] Scanner callback died and removed for $pkgName")
        }
    } catch (_: Throwable) {}
    ensureScannerCallbackHooked(callback)
    sysLog("[SysBle] Registered target BLE scanner for $pkgName (total active: ${activeBleScanners.size})")
}

private fun LocationHooker.startSystemBleHeartbeat(classLoader: ClassLoader) {
    if (isBleTimerStarted) return
    synchronized(activeBleScanners) {
        if (isBleTimerStarted) return
        isBleTimerStarted = true
    }

    val timer = ownReloadTimer(Timer("LocationSpoofer-SysBleHeartbeat", true))
    timer.schedule(object : TimerTask() {
        override fun run() {
            try {
                if (activeBleScanners.isEmpty()) return
                val config = readConfig() ?: return
                if (!config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) return

                dispatchFakeBeaconsToActiveScanners(config, classLoader)
            } catch (_: Throwable) {}
        }
    }, 1200L, 1200L)
}

private fun LocationHooker.dispatchFakeBeaconsToActiveScanners(config: JSONObject, classLoader: ClassLoader) {
    val bluetoothArray = config.optJSONArray("bluetooth_json") ?: return
    if (bluetoothArray.length() == 0) return

    for ((binder, info) in activeBleScanners) {
        if (!binder.isBinderAlive) {
            activeBleScanners.remove(binder)
            continue
        }
        val callback = info.callbackRef.get()
        if (callback != null) {
            try {
                deliverBleScanResults(config, callback, classLoader)
            } catch (e: Throwable) {
                activeBleScanners.remove(binder)
            }
        }
    }
}

private fun LocationHooker.ensureScannerCallbackHooked(callback: Any) {
    val clazz = callback.javaClass
    if (hookedCallbackClasses.putIfAbsent(clazz, true) != null) return
    val methods = arrayOf("onScanResult", "onBatchScanResults")
    for (mName in methods) {
        try {
            XposedHelpers.hookAllMethods(clazz, mName) { innerChain, _ ->
                val binder = (innerChain.thisObject as? IInterface)?.asBinder() ?: (innerChain.thisObject as? IBinder)
                val config = readConfig()
                val isGlobal = config?.optBoolean("system_hook_global_mode", false) == true
                val isTarget = (binder != null && activeBleScanners.containsKey(binder)) || isGlobal
                if (syntheticBleDelivery.current != true && isTarget && config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods null
                }
                return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }
}
