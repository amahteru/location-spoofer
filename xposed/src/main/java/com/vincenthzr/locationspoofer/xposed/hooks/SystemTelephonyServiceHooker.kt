package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.hooks.network.*
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemComponent
import com.vincenthzr.locationspoofer.xposed.diagnostics.HookStatus
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemClassLocator
import com.vincenthzr.locationspoofer.xposed.utils.XposedBridge
import com.vincenthzr.locationspoofer.xposed.utils.XposedHelpers

private fun sysLog(msg: String) = XposedBridge.log(msg)

@Volatile
internal var isTelephonyServiceHooked = false

internal fun LocationHooker.hookSystemTelephonyService(classLoader: ClassLoader) {
    if (isTelephonyServiceHooked) return

    var phoneServiceClass = SystemClassLocator.locate(SystemComponent.TELEPHONY_PHONE_MANAGER, classLoader)

    if (phoneServiceClass == null) {
        try {
            val smClass = XposedHelpers.findClassIfExists("android.os.ServiceManager", classLoader)
            val binder = if (smClass != null) {
                XposedHelpers.callStaticMethod(smClass, "getService", "phone") as? android.os.IBinder
            } else null
            if (binder != null) {
                phoneServiceClass = binder.javaClass
                HookStatus.classFound(SystemComponent.TELEPHONY_PHONE_MANAGER, binder.javaClass, "ServiceManager.getService")
                sysLog("[SysCell] Captured PhoneInterfaceManager from ServiceManager.getService(\"phone\"): ${binder.javaClass}")
            }
        } catch (_: Throwable) {}
    }

    if (phoneServiceClass == null) {
        sysLog("[SysCell] PhoneInterfaceManager not found in this classloader")
        return
    }

    if (hookedCallbackClasses.putIfAbsent(phoneServiceClass, true) != null) {
        isTelephonyServiceHooked = true
        return
    }

    try {
        XposedHelpers.hookAllMethods(phoneServiceClass, "getAllCellInfo") { chain, _ ->
            val config = readConfig() ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean("active", false)) {
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            val explicitPkg = SystemHookUtils.extractPackageName(chain.args)
            val isTarget = SystemHookUtils.isTargetCaller(chain.thisObject, explicitPkg, config)

            if (!isTarget) {
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            val mockCell = config.optBoolean("mock_cell", true)
            if (!mockCell) {
                sysLog("[SysCell] getAllCellInfo suppressed (mock_cell off) for ${explicitPkg ?: "caller"}")
                return@hookAllMethods java.util.ArrayList<Any>()
            }

            val lat = config.optDouble("lat", 0.0)
            val lng = config.optDouble("lng", 0.0)

            try {
                val fakeCellList = buildFakeCellInfoList(classLoader, lat, lng, config)
                sysLog("[SysCell] Dispatched ${fakeCellList.size} fake CellInfo items to ${explicitPkg ?: "caller"}"
                )
                return@hookAllMethods fakeCellList
            } catch (e: Throwable) {
                sysLog("[SysCell] buildFakeCellInfoList failed: $e")
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        }
        sysLog("[SysCell] PhoneInterfaceManager.getAllCellInfo hooked")
    } catch (e: Throwable) {
        sysLog("[SysCell] hook getAllCellInfo failed: $e")
    }

    try {
        XposedHelpers.hookAllMethods(phoneServiceClass, "getCellLocation") { chain, method ->
            val config = readConfig()
            if (config == null || !config.optBoolean("active", false)) {
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
            val explicitPkg = SystemHookUtils.extractPackageName(chain.args)
            if (!SystemHookUtils.isTargetCaller(chain.thisObject, explicitPkg, config)) {
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
            val returnType = (method as? java.lang.reflect.Method)?.returnType
                ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            try {
                val identity = if (config.optBoolean("mock_cell", true)) registeredCellIdentity(classLoader, config) else null
                val result = fakeCellLocation(returnType, identity, classLoader)
                sysLog("[SysCell] Dispatched fake cell location (${returnType.simpleName}) to ${explicitPkg ?: "caller"}: $identity")
                return@hookAllMethods result
            } catch (t: Throwable) {
                sysLog("[SysCell] build fake cell location failed: $t")
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        }
        sysLog("[SysCell] PhoneInterfaceManager.getCellLocation hooked")
    } catch (e: Throwable) {
        sysLog("[SysCell] hook getCellLocation failed: $e")
    }

    try {
        XposedHelpers.hookAllMethods(phoneServiceClass, "requestCellInfoUpdate") { chain, _ ->
            val config = readConfig() ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean("active", false)) {
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            val explicitPkg = SystemHookUtils.extractPackageName(chain.args)
            val isTarget = SystemHookUtils.isTargetCaller(chain.thisObject, explicitPkg, config)

            if (!isTarget) {
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            val mockCell = config.optBoolean("mock_cell", true)

            val lat = config.optDouble("lat", 0.0)
            val lng = config.optDouble("lng", 0.0)

            try {
                val callback = chain.args.firstOrNull { arg ->
                    arg != null && (
                        LocationHooker.hasTypeByName(arg.javaClass, "android.telephony.ICellInfoCallback") ||
                        arg.javaClass.name.contains("CellInfoCallback")
                    )
                }
                if (callback != null) {
                    val fakeCellList = if (mockCell) buildFakeCellInfoList(classLoader, lat, lng, config) else java.util.ArrayList<Any>()
                    val onCellInfoMethod = callback.javaClass.methods.firstOrNull { it.name == "onCellInfo" }
                    if (onCellInfoMethod != null) {
                        onCellInfoMethod.invoke(callback, fakeCellList)
                        sysLog("[SysCell] Intercepted requestCellInfoUpdate for ${explicitPkg ?: "caller"}, dispatched ${fakeCellList.size} fake cells directly"
                        )
                        return@hookAllMethods null
                    }
                }
            } catch (e: Throwable) {
                sysLog("[SysCell] intercept requestCellInfoUpdate failed: $e")
            }
            return@hookAllMethods chain.proceed(chain.args.toTypedArray())
        }
        sysLog("[SysCell] PhoneInterfaceManager.requestCellInfoUpdate hooked")
    } catch (e: Throwable) {
        sysLog("[SysCell] hook requestCellInfoUpdate failed: $e")
    }

    isTelephonyServiceHooked = true
}

internal fun LocationHooker.hookSystemTelephonyRegistry(classLoader: ClassLoader) {
    val registryClass = SystemClassLocator.locate(SystemComponent.TELEPHONY_REGISTRY, classLoader) ?: return
    if (hookedCallbackClasses.putIfAbsent(registryClass, true) != null) return
    val delivery = TelephonyCallbackDelivery(this, classLoader)
    vendorExtraHooks.add(delivery)
    delivery.install(registryClass)
}

private fun LocationHooker.registeredCellIdentity(classLoader: ClassLoader, config: org.json.JSONObject): Any? {
    val cells = buildFakeCellInfoList(classLoader, config.optDouble("lat", 0.0), config.optDouble("lng", 0.0), config)
    val registered = cells.firstOrNull { runCatching { XposedHelpers.callMethod(it, "isRegistered") == true }.getOrDefault(false) }
        ?: cells.firstOrNull()
    return registered?.let { XposedHelpers.callMethod(it, "getCellIdentity") }
}

private fun fakeCellLocation(returnType: Class<*>, identity: Any?, classLoader: ClassLoader): Any {
    if (identity != null && returnType.isInstance(identity)) return identity
    if (returnType.name.contains("CellIdentity")) {
        return XposedHelpers.newInstance(XposedHelpers.findClass("android.telephony.CellIdentityGsm", classLoader))
    }
    val area = identity?.let { cellValue(it, "getLac", "getTac") } ?: -1
    val id = identity?.let { cellValue(it, "getCid", "getCi") } ?: -1
    if (returnType == android.os.Bundle::class.java) {
        return android.os.Bundle().apply {
            putInt("lac", area)
            putInt("cid", id)
            putInt("psc", -1)
        }
    }
    val location = XposedHelpers.newInstance(XposedHelpers.findClass("android.telephony.gsm.GsmCellLocation", classLoader))
    if (identity != null) XposedHelpers.callMethod(location, "setLacAndCid", area, id)
    check(returnType.isInstance(location)) { "unsupported getCellLocation return type ${returnType.name}" }
    return location
}

private fun cellValue(identity: Any, vararg getters: String): Int? = getters.firstNotNullOfOrNull { name ->
    runCatching { XposedHelpers.callMethod(identity, name) as? Int }.getOrNull()
}
