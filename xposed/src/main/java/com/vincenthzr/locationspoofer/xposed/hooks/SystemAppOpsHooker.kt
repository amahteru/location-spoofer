package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemComponent
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemClassLocator
import com.vincenthzr.locationspoofer.xposed.utils.XposedBridge
import com.vincenthzr.locationspoofer.xposed.utils.XposedHelpers

private fun sysLog(msg: String) = XposedBridge.log(msg)

private const val OP_MOCK_LOCATION = 58
private const val MODE_IGNORED = 1

private fun withIgnoredMode(result: Any?): Any? {
    if (result is Int) return MODE_IGNORED
    if (result == null) return null
    try {
        XposedHelpers.setIntField(result, "mOpMode", MODE_IGNORED)
    } catch (t: Throwable) {
        sysLog("[SysHook] rewrite SyncNotedAppOp mode failed: $t")
    }
    return result
}

internal fun LocationHooker.hookSystemAppOpsService(classLoader: ClassLoader) {
    val appOpsServiceClass = SystemClassLocator.locate(SystemComponent.APPOPS_SERVICE, classLoader)

    if (appOpsServiceClass != null && hookedCallbackClasses.putIfAbsent(appOpsServiceClass, true) == null) {
        val opMethods = arrayOf("checkOperation", "noteOperation", "checkAudioOperation")
        for (methodName in opMethods) {
            try {
                XposedHelpers.hookAllMethods(appOpsServiceClass, methodName) { chain, method ->
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val codeArg = chain.args.firstOrNull { it is Int } as? Int
                        if (codeArg == OP_MOCK_LOCATION) {
                            val explicitPkg = SystemHookUtils.extractPackageName(chain.args)
                            if (SystemHookUtils.isTargetCaller(chain.thisObject, explicitPkg, config)) {
                                val returnType = (method as? java.lang.reflect.Method)?.returnType
                                if (returnType == Int::class.javaPrimitiveType) return@hookAllMethods MODE_IGNORED
                                val result = chain.proceed(chain.args.toTypedArray())
                                return@hookAllMethods withIgnoredMode(result)
                            }
                        }
                    }
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}
        }
        sysLog("[SysHook] AppOpsService OP_MOCK_LOCATION hooked")
    }

    try {
        val settingsSecureClass = XposedHelpers.findClassIfExists("android.provider.Settings\$Secure", classLoader)
        if (settingsSecureClass != null) {
            XposedHelpers.hookAllMethods(settingsSecureClass, "getString") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val key = chain.args.firstOrNull { it is String } as? String
                    if (key == "mock_location" || key == "allow_mock_location") {
                        return@hookAllMethods "0"
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        }
    } catch (_: Throwable) {}
}
