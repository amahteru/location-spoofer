package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.hooks.network.*
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemComponent
import com.vincenthzr.locationspoofer.xposed.diagnostics.HookStatus
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemClassLocator
import com.vincenthzr.locationspoofer.xposed.utils.XposedBridge
import com.vincenthzr.locationspoofer.xposed.utils.XposedHelpers

private fun sysLog(msg: String) = XposedBridge.log(msg)

/**
 * 电话底层服务（PhoneInterfaceManager in com.android.phone）与 TelephonyRegistry (in system_server)
 * 基站小区拦截与伪造模块
 *
 * 核心原理：
 * 1. 全设备所有应用同步调用 TelephonyManager.getAllCellInfo() 与 getCellLocation()
 *    都会经由 ITelephony 远程 IPC 路由至 com.android.phone 中的 PhoneInterfaceManager。
 * 2. 异步基站监听（PhoneStateListener / TelephonyCallback）则通过 system_server 中的 TelephonyRegistry 分发。
 *
 * 本模块双管齐下：
 * - 在 com.android.phone 中挂载 PhoneInterfaceManager，拦截 getAllCellInfo / getCellLocation；
 * - 在 system_server 中挂载 TelephonyRegistry，按目标应用改写基站回调；
 * 派发与目标模拟坐标一致的 GSM/LTE/5G NR 基站小区数据，彻底消除基站与 GPS 的位置冲突。
 */

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

    // =========================================================================
    // 1. getAllCellInfo：向目标应用派发伪造基站列表 (GSM/LTE/5G NR)
    // =========================================================================
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
                // 与 Wi-Fi 一致：开关关闭时不能把真实基站信息透传给已被模拟 GPS 覆盖的目标应用
                // （会暴露真实城市/地区，与伪造坐标矛盾触发风控），也不伪造假数据，直接返回空列表。
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

    // =========================================================================
    // 2. getCellLocation：返回 getAllCellInfo 同一份伪造列表里的已注册小区，两个接口给出的基站一致
    // =========================================================================
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
                // 关闭基站模拟时 getAllCellInfo 返回空列表，这里同样报告"没有小区"，不放行真实基站
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

    // =========================================================================
    // 3. requestCellInfoUpdate：拦截现代 Android 异步基站更新请求（高德/淘宝核心）
    // =========================================================================
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
                    // 开关关闭时投递空列表而不是放行真实回调，避免真实基站信息通过异步回调泄露
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

/** Preserve the registry cache; transform only permission-checked recipient callbacks. */
internal fun LocationHooker.hookSystemTelephonyRegistry(classLoader: ClassLoader) {
    val registryClass = SystemClassLocator.locate(SystemComponent.TELEPHONY_REGISTRY, classLoader) ?: return
    if (hookedCallbackClasses.putIfAbsent(registryClass, true) != null) return
    val delivery = TelephonyCallbackDelivery(this, classLoader)
    vendorExtraHooks.add(delivery)
    delivery.install(registryClass)
}

/** 伪造基站列表里的已注册小区（没有标记注册的取第一个）的 CellIdentity；列表为空时返回 null */
private fun LocationHooker.registeredCellIdentity(classLoader: ClassLoader, config: org.json.JSONObject): Any? {
    val cells = buildFakeCellInfoList(classLoader, config.optDouble("lat", 0.0), config.optDouble("lng", 0.0), config)
    val registered = cells.firstOrNull { runCatching { XposedHelpers.callMethod(it, "isRegistered") == true }.getOrDefault(false) }
        ?: cells.firstOrNull()
    return registered?.let { XposedHelpers.callMethod(it, "getCellIdentity") }
}

/**
 * 按 getCellLocation 在当前系统上的返回类型包装小区：Android 10 起是 CellIdentity，更早是 Bundle 或 CellLocation。
 * [identity] 为 null 时返回空小区（各字段为"未知"），客户端会把它当成没有基站信息。
 */
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

/** 依次尝试几个取值方法（GSM/WCDMA 用 getLac/getCid，LTE 用 getTac/getCi），都没有时返回 null */
private fun cellValue(identity: Any, vararg getters: String): Int? = getters.firstNotNullOfOrNull { name ->
    runCatching { XposedHelpers.callMethod(identity, name) as? Int }.getOrNull()
}
