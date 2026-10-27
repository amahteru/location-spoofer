package com.vincenthzr.locationspoofer.xposed.hooks.network

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookCellEnvironment(
    classLoader: ClassLoader,
    isCoreSystemProcess: Boolean = false
) {
    XposedBridge.logOpenCellId("Installing cell hooks classLoader=$classLoader")

    fun handleCellMethod(chain: XposedInterface.Chain, method: java.lang.reflect.Executable): Any? {
        val methodName = method.name
        val config = readConfig()
        if (config == null) {
            XposedBridge.logOpenCellIdEvery(
                "$methodName:config-null",
                "$methodName skipped: config=null",
                30_000L
            )
            return chain.proceed(chain.args.toTypedArray())
        }
        if (!config.optBoolean("active", false)) {
            XposedBridge.logOpenCellIdEvery(
                "$methodName:inactive",
                "$methodName skipped: active=false",
                30_000L
            )
            return chain.proceed(chain.args.toTypedArray())
        }
        val lat = config.optDouble("lat", 0.0)
        val lng = config.optDouble("lng", 0.0)
        val mockCellForLog = config.optBoolean("mock_cell", true)
        val cellCountForLog = config.optJSONArray("cell_json")?.length() ?: 0
        XposedBridge.logOpenCellIdEvery(
            "$methodName:called:$mockCellForLog:$cellCountForLog",
            "$methodName called active=true mockCell=$mockCellForLog cellJsonCount=$cellCountForLog lat=$lat lng=$lng"
        )

        when (methodName) {
            "getCellLocation" -> {
                try {
                    val mockCell = config.optBoolean("mock_cell", true)
                    if (mockCell) {
                        val gsmCellLocationClass = XposedHelpers.findClass(
                            "android.telephony.gsm.GsmCellLocation", classLoader
                        )
                        val fakeLocation = XposedHelpers.newInstance(gsmCellLocationClass)
                        val (lac, cid) = registeredCellCodes(config, lat, lng)
                        XposedHelpers.callMethod(fakeLocation, "setLacAndCid", lac, cid)
                        XposedBridge.logOpenCellId("getCellLocation returning GsmCellLocation lac=$lac cid=$cid")
                        return fakeLocation
                    } else {
                        XposedBridge.logOpenCellId("getCellLocation returning null because mock_cell=false")
                        return null
                    }
                } catch (e: Throwable) {
                    XposedBridge.logOpenCellId("getCellLocation failed: $e")
                    return null
                }
            }

            "getAllCellInfo" -> {
                try {
                    if (config.optBoolean("mock_cell", true)) {
                        val fakeCells = buildFakeCellInfoList(classLoader, lat, lng, config)
                        XposedBridge.logOpenCellIdEvery(
                            "getAllCellInfo:return:${fakeCells.size}",
                            "getAllCellInfo returning fakeCells=${fakeCells.size}"
                        )
                        return fakeCells
                    } else {
                        XposedBridge.logOpenCellId("getAllCellInfo returning empty because mock_cell=false")
                        return java.util.ArrayList<Any>()
                    }
                } catch (e: Throwable) {
                    XposedBridge.logOpenCellId("getAllCellInfo build failed: $e")
                    return java.util.ArrayList<Any>()
                }
            }

            "getNeighboringCellInfo" -> {
                XposedBridge.logOpenCellId("getNeighboringCellInfo returning empty list")
                return java.util.ArrayList<Any>()
            }
        }
        return chain.proceed(chain.args.toTypedArray())
    }

    try {
        XposedHelpers.hookMethod(
            "android.telephony.TelephonyManager",
            classLoader,
            "getAllCellInfo"
        ) { chain, method -> return@hookMethod handleCellMethod(chain, method) }

        XposedHelpers.hookMethod(
            "android.telephony.TelephonyManager",
            classLoader,
            "getCellLocation"
        ) { chain, method -> return@hookMethod handleCellMethod(chain, method) }

        XposedHelpers.hookMethod(
            "android.telephony.TelephonyManager",
            classLoader,
            "getNeighboringCellInfo"
        ) { chain, method -> return@hookMethod handleCellMethod(chain, method) }
        XposedBridge.logOpenCellId("Installed TelephonyManager getAllCellInfo/getCellLocation/getNeighboringCellInfo hooks")
    } catch (e: Throwable) {
        XposedBridge.logOpenCellId("Install basic TelephonyManager cell hooks failed: $e")
    }

    fun handleTelephonyMeta(
        chain: XposedInterface.Chain,
        method: java.lang.reflect.Executable
    ): Any? {
        val methodName = method.name
        val config = readConfig()
        if (config == null) {
            XposedBridge.logOpenCellIdEvery(
                "$methodName:config-null",
                "$methodName skipped: config=null",
                30_000L
            )
            return chain.proceed(chain.args.toTypedArray())
        }
        if (!config.optBoolean("active", false)) {
            XposedBridge.logOpenCellIdEvery(
                "$methodName:inactive",
                "$methodName skipped: active=false",
                30_000L
            )
            return chain.proceed(chain.args.toTypedArray())
        }
        val mockCell = config.optBoolean("mock_cell", true)
        val cellArray = if (mockCell) config.optJSONArray("cell_json") else null
        XposedBridge.logOpenCellIdEvery(
            "$methodName:called:$mockCell:${cellArray?.length() ?: 0}",
            "$methodName called mockCell=$mockCell cellJsonCount=${cellArray?.length() ?: 0}"
        )
        when (methodName) {
            "getNetworkOperator" -> {
                val cell = registeredCellJson(cellArray)
                if (cell != null) {
                    val mcc = positiveJsonInt(cell, "mcc", default = 460)
                    val mnc = positiveJsonInt(cell, "mnc", "net", default = 0)
                    val operator = String.format(java.util.Locale.US, "%d%02d", mcc, mnc)
                    XposedBridge.logOpenCellIdEvery(
                        "getNetworkOperator:return:$operator",
                        "getNetworkOperator returning $operator"
                    )
                    return operator
                } else if (!mockCell) {
                    return ""
                }
            }

            "getNetworkOperatorName" -> {
                val cell = registeredCellJson(cellArray)
                if (cell != null) {
                    val mnc = positiveJsonInt(
                        cell,
                        "mnc",
                        "net",
                        default = 0
                    )
                    val result = when (mnc) {
                        0, 2, 7 -> "中国移动"
                        1, 6, 9 -> "中国联通"
                        3, 5, 11 -> "中国电信"
                        else -> "中国移动"
                    }
                    XposedBridge.logOpenCellIdEvery(
                        "getNetworkOperatorName:return:${result}",
                        "getNetworkOperatorName returning ${result}"
                    )
                    return result
                } else if (!mockCell) {
                    return ""
                }
            }

            "getSimOperator" -> {
            }

            "getSimOperatorName" -> {
            }

            "getNetworkType" -> return if (mockCell) 13 else 0
            "getDataNetworkType" -> return if (mockCell) 13 else 0
            "getPhoneType" -> return 1
            "getServiceState", "getServiceStateForSlot" -> {
                if (mockCell) buildFakeServiceState(classLoader, cellArray)?.let {
                    XposedBridge.logOpenCellIdEvery(
                        "$methodName:return-service-state",
                        "$methodName returning fake ServiceState"
                    )
                    return it
                }
            }

            "getSignalStrength" -> {
                if (mockCell) buildFakeSignalStrength(classLoader, config)?.let {
                    XposedBridge.logOpenCellIdEvery(
                        "getSignalStrength:return-signal-strength",
                        "getSignalStrength returning fake SignalStrength"
                    )
                    return it
                }
            }
        }
        return chain.proceed(chain.args.toTypedArray())
    }

    val telephonyMetaMethods = listOf(
        "getNetworkOperator", "getNetworkOperatorName",
        "getNetworkType", "getDataNetworkType", "getPhoneType",
        "getServiceState", "getServiceStateForSlot", "getSignalStrength"
    )
    for (method in telephonyMetaMethods) {
        try {
            XposedHelpers.hookAllMethods(
                XposedHelpers.findClass("android.telephony.TelephonyManager", classLoader),
                method
            ) { chain, method -> return@hookAllMethods handleTelephonyMeta(chain, method) }
            XposedBridge.logOpenCellId("Installed TelephonyManager.$method hook")
        } catch (e: Throwable) {
            XposedBridge.logOpenCellId("Install TelephonyManager.$method hook failed: $e")
        }
    }

    try {
        XposedHelpers.hookMethod(
            "android.telephony.TelephonyManager", classLoader, "listen",
            "android.telephony.PhoneStateListener",
            Int::class.javaPrimitiveType!!
        ) { chain, method ->
            val config = readConfig()
            if (config == null) {
                XposedBridge.logOpenCellIdEvery(
                    "listen:config-null",
                    "TelephonyManager.listen skipped: config=null",
                    30_000L
                )
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
            if (!config.optBoolean("active", false)) {
                XposedBridge.logOpenCellIdEvery(
                    "listen:inactive",
                    "TelephonyManager.listen skipped: active=false",
                    30_000L
                )
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
            val listener =
                chain.args[0] ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            val originalEvents = chain.args[1] as Int
            val lat = config.optDouble("lat", 0.0)
            val lng = config.optDouble("lng", 0.0)
            val mockCell = config.optBoolean("mock_cell", true)
            val cellJsonCount = config.optJSONArray("cell_json")?.length() ?: 0
            val needsCellInfo = (originalEvents and 0x400) != 0
            val needsCellLocation = (originalEvents and 0x10) != 0
            val needsServiceState = (originalEvents and 0x1) != 0
            val needsSignalStrength = (originalEvents and 0x100) != 0
            if (!needsCellInfo && !needsCellLocation && !needsServiceState && !needsSignalStrength) {
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
            XposedBridge.logOpenCellIdEvery(
                "listen:called:${listener.javaClass.name}:$originalEvents:$mockCell:$cellJsonCount",
                "TelephonyManager.listen called listener=${listener.javaClass.name} events=0x${
                    originalEvents.toString(
                        16
                    )
                } mockCell=$mockCell cellJsonCount=$cellJsonCount"
            )
            val fakeCells by lazy {
                if (mockCell) {
                    buildFakeCellInfoList(classLoader, lat, lng, config)
                } else {
                    java.util.ArrayList<Any>()
                }
            }
            if ((originalEvents and 0x10) != 0) {
                try {
                    if (mockCell) {
                        val gsmCellLocationClass = XposedHelpers.findClass(
                            "android.telephony.gsm.GsmCellLocation", classLoader
                        )
                        val fakeLocation =
                            XposedHelpers.newInstance(gsmCellLocationClass)
                        val (lac, cid) = registeredCellCodes(config, lat, lng)
                        XposedHelpers.callMethod(
                            fakeLocation,
                            "setLacAndCid",
                            lac,
                            cid
                        )
                        XposedHelpers.callMethod(
                            listener,
                            "onCellLocationChanged",
                            fakeLocation
                        )
                        XposedBridge.logOpenCellIdEvery(
                            "listen:onCellLocationChanged:$lac:$cid",
                            "listen dispatched onCellLocationChanged lac=$lac cid=$cid"
                        )
                    }
                } catch (e: Throwable) {
                    XposedBridge.logOpenCellId("listen onCellLocationChanged failed: $e")
                }
            }
            if ((originalEvents and 0x400) != 0) {
                try {
                    XposedHelpers.callMethod(listener, "onCellInfoChanged", fakeCells)
                    XposedBridge.logOpenCellIdEvery(
                        "listen:onCellInfoChanged:${fakeCells.size}",
                        "listen dispatched onCellInfoChanged fakeCells=${fakeCells.size}"
                    )
                } catch (e: Throwable) {
                    XposedBridge.logOpenCellId("listen onCellInfoChanged failed: $e")
                }
            }
            if ((originalEvents and 0x1) != 0) {
                try {
                    buildFakeServiceState(classLoader, config.optJSONArray("cell_json"))
                        ?.let {
                            XposedHelpers.callMethod(
                                listener,
                                "onServiceStateChanged",
                                it
                            )
                        }
                    XposedBridge.logOpenCellIdEvery(
                        "listen:onServiceStateChanged",
                        "listen dispatched onServiceStateChanged"
                    )
                } catch (e: Throwable) {
                    XposedBridge.logOpenCellId("listen onServiceStateChanged failed: $e")
                }
            }
            if ((originalEvents and 0x100) != 0) {
                try {
                    buildFakeSignalStrength(classLoader, config)
                        ?.let {
                            XposedHelpers.callMethod(
                                listener,
                                "onSignalStrengthsChanged",
                                it
                            )
                        }
                    XposedBridge.logOpenCellIdEvery(
                        "listen:onSignalStrengthsChanged",
                        "listen dispatched onSignalStrengthsChanged"
                    )
                } catch (e: Throwable) {
                    XposedBridge.logOpenCellId("listen onSignalStrengthsChanged failed: $e")
                }
            }
            var events = originalEvents
            events = events and 0x1.inv()
            events = events and 0x10.inv()
            events = events and 0x100.inv()
            events = events and 0x400.inv()
            val newArgs = chain.args.toTypedArray().clone()
            if (newArgs.size > 1) {
                newArgs[1] = events
            }
            XposedBridge.logOpenCellIdEvery(
                "listen:sanitized:$originalEvents:$events",
                "TelephonyManager.listen sanitized events=0x${events.toString(16)}"
            )
            return@hookMethod chain.proceed(newArgs)
        }
        XposedBridge.logOpenCellId("Installed TelephonyManager.listen hook")
    } catch (e: Throwable) {
        XposedBridge.logOpenCellId("Install TelephonyManager.listen hook failed: $e")
    }

    try {
        XposedHelpers.hookAllMethods(
            XposedHelpers.findClass("android.telephony.TelephonyManager", classLoader),
            "requestCellInfoUpdate"
        ) { chain, method ->
            val config = readConfig()
            if (config == null) {
                XposedBridge.logOpenCellIdEvery(
                    "requestCellInfoUpdate:config-null",
                    "requestCellInfoUpdate skipped: config=null",
                    30_000L
                )
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
            if (!config.optBoolean("active", false)) {
                XposedBridge.logOpenCellIdEvery(
                    "requestCellInfoUpdate:inactive",
                    "requestCellInfoUpdate skipped: active=false",
                    30_000L
                )
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            val executor =
                chain.args[0] as? java.util.concurrent.Executor ?: return@hookAllMethods null
            val callback = chain.args[1] ?: return@hookAllMethods null
            XposedBridge.logOpenCellIdEvery(
                "requestCellInfoUpdate:called:${callback.javaClass.name}",
                "requestCellInfoUpdate called callback=${callback.javaClass.name} args=${chain.args.size}"
            )

            val mockCell = config.optBoolean("mock_cell", true)
            val lat = config.optDouble("lat", 0.0)
            val lng = config.optDouble("lng", 0.0)

            val fakeCells = if (mockCell) {
                buildFakeCellInfoList(classLoader, lat, lng, config)
            } else {
                java.util.ArrayList<Any>()
            }

            executor.execute {
                try {
                    XposedHelpers.callMethod(callback, "onCellInfo", fakeCells)
                    XposedBridge.logOpenCellId("requestCellInfoUpdate dispatched onCellInfo fakeCells=${fakeCells.size}")
                } catch (e: Throwable) {
                    XposedBridge.logOpenCellId("requestCellInfoUpdate onCellInfo failed: $e")
                }
            }
            return@hookAllMethods null
        }
        XposedBridge.logOpenCellId("Installed TelephonyManager.requestCellInfoUpdate hook")
    } catch (e: Throwable) {
        XposedBridge.logOpenCellId("Install TelephonyManager.requestCellInfoUpdate hook failed: $e")
    }

    try {
        XposedHelpers.hookAllMethods(
            XposedHelpers.findClass("android.telephony.TelephonyManager", classLoader),
            "registerTelephonyCallback"
        ) { chain, method ->
            val config = readConfig()
            if (config == null) {
                XposedBridge.logOpenCellIdEvery(
                    "registerTelephonyCallback:config-null",
                    "registerTelephonyCallback skipped: config=null",
                    30_000L
                )
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
            if (!config.optBoolean("active", false)) {
                XposedBridge.logOpenCellIdEvery(
                    "registerTelephonyCallback:inactive",
                    "registerTelephonyCallback skipped: active=false",
                    30_000L
                )
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            val callback = chain.args.firstOrNull { arg ->
                arg != null && (arg.javaClass.interfaces.any { iface ->
                    iface.name.contains("TelephonyCallback")
                } || LocationHooker.hasTypeByName(
                    arg.javaClass,
                    "android.telephony.TelephonyCallback"
                ))
            }
            if (callback == null) {
                XposedBridge.logOpenCellId("registerTelephonyCallback called but callback not found args=${chain.args.map { it?.javaClass?.name }}")
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            val callbackClass = callback.javaClass
            XposedBridge.logOpenCellIdEvery(
                "registerTelephonyCallback:called:${callbackClass.name}",
                "registerTelephonyCallback called callback=${callbackClass.name} interfaces=${callbackClass.interfaces.joinToString { it.name }}"
            )

            if (isTelephonyCallbackListener(
                    classLoader,
                    callback,
                    "CellInfoListener"
                )
            ) {
                XposedBridge.logOpenCellIdEvery(
                    "registerTelephonyCallback:CellInfoListener:${callbackClass.name}",
                    "registerTelephonyCallback installing CellInfoListener hook on ${callbackClass.name}",
                    60_000L
                )
                XposedHelpers.hookAllMethods(
                    callbackClass,
                    "onCellInfoChanged"
                ) { innerChain, _ ->
                    val freshConfig = readConfig() ?: return@hookAllMethods innerChain.proceed(
                        innerChain.args.toTypedArray()
                    )
                    if (!freshConfig.optBoolean(
                            "active",
                            false
                        )
                    ) return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                    val lat = freshConfig.optDouble("lat", 0.0)
                    val lng = freshConfig.optDouble("lng", 0.0)
                    val fakeCells = buildFakeCellInfoList(
                        classLoader,
                        lat,
                        lng,
                        freshConfig
                    )
                    val newArgs = innerChain.args.toTypedArray().clone()
                    if (newArgs.isNotEmpty()) {
                        newArgs[0] = fakeCells
                    }
                    XposedBridge.logOpenCellId("TelephonyCallback.onCellInfoChanged injected fakeCells=${fakeCells.size}")
                    return@hookAllMethods innerChain.proceed(newArgs)
                }
            }

            if (isTelephonyCallbackListener(
                    classLoader,
                    callback,
                    "ServiceStateListener"
                )
            ) {
                XposedBridge.logOpenCellIdEvery(
                    "registerTelephonyCallback:ServiceStateListener:${callbackClass.name}",
                    "registerTelephonyCallback installing ServiceStateListener hook on ${callbackClass.name}",
                    60_000L
                )
                XposedHelpers.hookAllMethods(
                    callbackClass,
                    "onServiceStateChanged"
                ) { innerChain, _ ->
                    val freshConfig = readConfig() ?: return@hookAllMethods innerChain.proceed(
                        innerChain.args.toTypedArray()
                    )
                    if (!freshConfig.optBoolean(
                            "active",
                            false
                        )
                    ) return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                    val fakeState = buildFakeServiceState(
                        classLoader,
                        freshConfig.optJSONArray("cell_json")
                    )
                    val newArgs = innerChain.args.toTypedArray().clone()
                    if (fakeState != null && newArgs.isNotEmpty()) {
                        newArgs[0] = fakeState
                        XposedBridge.logOpenCellId("TelephonyCallback.onServiceStateChanged injected fake ServiceState")
                    }
                    return@hookAllMethods innerChain.proceed(newArgs)
                }
            }

            if (isTelephonyCallbackListener(
                    classLoader,
                    callback,
                    "SignalStrengthsListener"
                )
            ) {
                XposedBridge.logOpenCellIdEvery(
                    "registerTelephonyCallback:SignalStrengthsListener:${callbackClass.name}",
                    "registerTelephonyCallback installing SignalStrengthsListener hook on ${callbackClass.name}",
                    60_000L
                )
                XposedHelpers.hookAllMethods(
                    callbackClass,
                    "onSignalStrengthsChanged"
                ) { innerChain, _ ->
                    val freshConfig = readConfig() ?: return@hookAllMethods innerChain.proceed(
                        innerChain.args.toTypedArray()
                    )
                    if (!freshConfig.optBoolean(
                            "active",
                            false
                        )
                    ) return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                    val fakeSignal = buildFakeSignalStrength(classLoader, freshConfig)
                    val newArgs = innerChain.args.toTypedArray().clone()
                    if (fakeSignal != null && newArgs.isNotEmpty()) {
                        newArgs[0] = fakeSignal
                        XposedBridge.logOpenCellId("TelephonyCallback.onSignalStrengthsChanged injected fake SignalStrength")
                    }
                    return@hookAllMethods innerChain.proceed(newArgs)
                }
            }
            return@hookAllMethods chain.proceed(chain.args.toTypedArray())
        }
        XposedBridge.logOpenCellId("Installed TelephonyManager.registerTelephonyCallback hook")
    } catch (e: Throwable) {
        XposedBridge.logOpenCellId("Install TelephonyManager.registerTelephonyCallback hook failed: $e")
    }

    XposedBridge.logOpenCellId("Cell environment hooks installed")
}
