package io.github.amahteru.locationspoofer.xposed.hooks.network

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookWifiConnectionInfo(
    classLoader: ClassLoader,
    isCoreSystemProcess: Boolean = false
) {
    try {
        val wifiInfoMethods = listOf(
            "getBSSID", "getMacAddress", "getSSID", "getNetworkId",
            "getRssi", "getLinkSpeed", "getFrequency", "getIpAddress"
        )
        for (method in wifiInfoMethods) {
            try {
                XposedHelpers.hookMethod(
                    "android.net.wifi.WifiInfo", classLoader, method
                ) { chain, method ->
                    val config =
                        readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
                    if (!config.optBoolean("active", false) || !config.optBoolean("mock_wifi", true)) {
                        return@hookMethod chain.proceed(chain.args.toTypedArray())
                    }
                    val wifiObj = config.optJSONObject("wifi_json")
                    val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
                    val connectedWifi =
                        if (isConnected) wifiObj.optJSONObject("connectedWifi") else null
                    if (!isConnected || connectedWifi == null) {
                        return@hookMethod chain.proceed(chain.args.toTypedArray())
                    }

                    when (method.name) {
                        "getBSSID" -> return@hookMethod connectedWifi.optString("bssid", "02:00:00:00:00:00")

                        "getMacAddress" -> return@hookMethod connectedWifi.optString("macAddress", "02:00:00:00:00:00")

                        "getSSID" -> {
                            val ssidVal = connectedWifi.optString("ssid", "")
                            val finalSsid =
                                if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") "" else ssidVal
                            return@hookMethod if (finalSsid.isEmpty()) "<unknown ssid>" else "\"$finalSsid\""
                        }

                        "getNetworkId" -> return@hookMethod connectedWifi.optInt("networkId", -1)

                        "getRssi" -> return@hookMethod connectedWifi.optInt("level", -127)

                        "getLinkSpeed" -> return@hookMethod connectedWifi.optInt("linkSpeed", -1)

                        "getFrequency" -> return@hookMethod connectedWifi.optInt("frequency", -1)

                        "getIpAddress" -> return@hookMethod 0x6401A8C0
                    }
                    return@hookMethod chain.proceed(chain.args.toTypedArray())
                }
            } catch (e: Throwable) {
            }
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiInfo", classLoader, "getSupplicantState"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean("active", false) || !config.optBoolean("mock_wifi", true)) {
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
            val wifiObj = config.optJSONObject("wifi_json")
            val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
            val connectedWifi =
                if (isConnected) wifiObj.optJSONObject("connectedWifi") else null
            if (!isConnected || connectedWifi == null) {
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
            try {
                val enumClass = XposedHelpers.findClass(
                    "android.net.wifi.SupplicantState", classLoader
                )
                return@hookMethod enumClass.getField("COMPLETED").get(null)
            } catch (e: Throwable) {
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }
    } catch (e: Throwable) {
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getConnectionInfo"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean("active", false) || !config.optBoolean("mock_wifi", true)) {
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
            val wifiObj = config.optJSONObject("wifi_json")
            val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
            val connectedWifi =
                if (isConnected) wifiObj.optJSONObject("connectedWifi") else null
            if (!isConnected || connectedWifi == null) {
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }

            val currentResult = chain.proceed(chain.args.toTypedArray())
            try {
                val ssidVal = connectedWifi.optString("ssid", "")
                    val finalSsid =
                        if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") "HOME_WIFI" else ssidVal
                    val bssidVal = connectedWifi.optString("bssid", "02:00:00:00:00:00")
                    val freqVal = connectedWifi.optInt("frequency", 2412)
                    val macAddressVal = connectedWifi.optString("macAddress", bssidVal)
                    val linkSpeedVal = connectedWifi.optInt("linkSpeed", 65)
                    val standardVal = connectedWifi.optInt("wifiStandard", 6)
                    val levelVal = connectedWifi.optInt("level", -65)
                    val networkIdVal = connectedWifi.optInt("networkId", 1)

                    if (currentResult != null) {
                        try {
                            XposedHelpers.setObjectField(
                                currentResult,
                                "mSSID",
                                "\"$finalSsid\""
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            val wifiSsidClass = XposedHelpers.findClassIfExists(
                                "android.net.wifi.WifiSsid",
                                classLoader
                            )
                            if (wifiSsidClass != null) {
                                val createMethod = XposedHelpers.findMethodExact(
                                    wifiSsidClass,
                                    "createFromAsciiEncoded",
                                    String::class.java
                                )
                                val wifiSsid = createMethod.invoke(null, finalSsid)
                                XposedHelpers.setObjectField(
                                    currentResult,
                                    "mWifiSsid",
                                    wifiSsid
                                )
                            }
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setObjectField(currentResult, "mBSSID", bssidVal)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setObjectField(
                                currentResult,
                                "mMacAddress",
                                macAddressVal
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(currentResult, "mRssi", levelVal)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(currentResult, "mFrequency", freqVal)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(
                                currentResult,
                                "mLinkSpeed",
                                linkSpeedVal
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(
                                currentResult,
                                "mNetworkId",
                                networkIdVal
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(
                                currentResult,
                                "mWifiStandard",
                                standardVal
                            )
                        } catch (e: Throwable) {
                        }
                    } else {
                        if (isCoreSystemProcess) return@hookMethod currentResult

                        var builtWithBuilder = false
                        var builtInfo: Any? = null
                        try {
                            val builderClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiInfo\$Builder",
                                classLoader
                            )
                            val builder = XposedHelpers.newInstance(builderClass)
                            try {
                                XposedHelpers.callMethod(builder, "setSsid", finalSsid.toByteArray(Charsets.UTF_8))
                            } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(builder, "setBssid", bssidVal) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(builder, "setRssi", levelVal) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(builder, "setFrequency", freqVal) } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(builder, "setLinkSpeed", linkSpeedVal) } catch (_: Throwable) {}
                            builtInfo = XposedHelpers.callMethod(builder, "build")
                            builtWithBuilder = (builtInfo != null)
                        } catch (e: Throwable) {
                        }

                        val fakeWifiInfo = if (builtWithBuilder) {
                            builtInfo!!
                        } else {
                            val wifiInfoClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiInfo",
                                classLoader
                            )
                            val info = XposedHelpers.newInstance(wifiInfoClass)
                            try {
                                XposedHelpers.setObjectField(
                                    info,
                                    "mSSID",
                                    "\"$finalSsid\""
                                )
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setObjectField(info, "mBSSID", bssidVal)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setObjectField(
                                    info,
                                    "mMacAddress",
                                    macAddressVal
                                )
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(info, "mRssi", levelVal)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(info, "mFrequency", freqVal)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(info, "mLinkSpeed", linkSpeedVal)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(info, "mNetworkId", networkIdVal)
                            } catch (e: Throwable) {
                            }
                            info
                        }
                        return@hookMethod fakeWifiInfo
                    }
                } catch (e: Throwable) {
                }
            return@hookMethod currentResult
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }
}
