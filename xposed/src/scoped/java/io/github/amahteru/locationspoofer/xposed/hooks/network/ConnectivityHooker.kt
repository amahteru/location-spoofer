package io.github.amahteru.locationspoofer.xposed.hooks.network

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookConnectivityLayer(
    classLoader: ClassLoader,
    isCoreSystemProcess: Boolean = false
) {
    val buildFakeNetworkInfo = fun(): Any? {
        try {
            val networkInfoClass =
                XposedHelpers.findClass("android.net.NetworkInfo", classLoader)
            val fakeNetworkInfo = XposedHelpers.newInstance(networkInfoClass, 1, 0, "WIFI", "")
            XposedHelpers.callMethod(fakeNetworkInfo, "setIsAvailable", true)
            try {
                val stateEnum =
                    XposedHelpers.findClass("android.net.NetworkInfo\$State", classLoader)
                XposedHelpers.setObjectField(
                    fakeNetworkInfo,
                    "mState",
                    stateEnum.getField("CONNECTED").get(null)
                )
            } catch (e: Throwable) {
            }
            try {
                val detailedStateEnum = XposedHelpers.findClass(
                    "android.net.NetworkInfo\$DetailedState",
                    classLoader
                )
                XposedHelpers.setObjectField(
                    fakeNetworkInfo,
                    "mDetailedState",
                    detailedStateEnum.getField("CONNECTED").get(null)
                )
            } catch (e: Throwable) {
            }
            return fakeNetworkInfo
        } catch (e: Throwable) {
            return null
        }
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.ConnectivityManager",
            classLoader,
            "getActiveNetworkInfo"
        ) { chain, method ->
            val result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig() ?: return@hookMethod result
            if (!config.optBoolean("active", false)) return@hookMethod result
            if (config.optBoolean("mock_wifi", true)) {
                val wifiObj = config.optJSONObject("wifi_json")
                val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
                val hasWifiData = isConnected && wifiObj.optJSONObject("connectedWifi") != null
                if (hasWifiData) {
                    val currentInfo = result
                    if (currentInfo != null) {
                        try {
                            XposedHelpers.callMethod(currentInfo, "setIsAvailable", true)
                            val stateEnum = XposedHelpers.findClass(
                                "android.net.NetworkInfo\$State",
                                classLoader
                            )
                            XposedHelpers.setObjectField(
                                currentInfo,
                                "mState",
                                stateEnum.getField("CONNECTED").get(null)
                            )
                            val detailedStateEnum = XposedHelpers.findClass(
                                "android.net.NetworkInfo\$DetailedState",
                                classLoader
                            )
                            XposedHelpers.setObjectField(
                                currentInfo,
                                "mDetailedState",
                                detailedStateEnum.getField("CONNECTED").get(null)
                            )
                            XposedHelpers.setObjectField(
                                currentInfo,
                                "mNetworkType",
                                1
                            )
                            XposedHelpers.setObjectField(currentInfo, "mTypeName", "WIFI")
                        } catch (e: Throwable) {
                        }
                    } else {
                        if (isCoreSystemProcess) return@hookMethod result
                        val fakeInfo = buildFakeNetworkInfo()
                        if (fakeInfo != null) {
                            return@hookMethod fakeInfo
                        }
                    }
                }
            }
            return@hookMethod result
        }
    } catch (e: Throwable) {
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.ConnectivityManager",
            classLoader,
            "getNetworkInfo",
            Int::class.javaPrimitiveType!!
        ) { chain, method ->
            val result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig() ?: return@hookMethod result
            if (!config.optBoolean("active", false)) return@hookMethod result
            if (config.optBoolean("mock_wifi", true)) {
                val wifiObj = config.optJSONObject("wifi_json")
                val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
                val hasWifiData = isConnected && wifiObj.optJSONObject("connectedWifi") != null
                if (hasWifiData) {
                    val currentInfo = result
                    if (currentInfo != null) {
                        try {
                            XposedHelpers.callMethod(currentInfo, "setIsAvailable", true)
                            val stateEnum = XposedHelpers.findClass(
                                "android.net.NetworkInfo\$State",
                                classLoader
                            )
                            XposedHelpers.setObjectField(
                                currentInfo,
                                "mState",
                                stateEnum.getField("CONNECTED").get(null)
                            )
                            val detailedStateEnum = XposedHelpers.findClass(
                                "android.net.NetworkInfo\$DetailedState",
                                classLoader
                            )
                            XposedHelpers.setObjectField(
                                currentInfo,
                                "mDetailedState",
                                detailedStateEnum.getField("CONNECTED").get(null)
                            )
                            XposedHelpers.setObjectField(
                                currentInfo,
                                "mNetworkType",
                                1
                            )
                            XposedHelpers.setObjectField(currentInfo, "mTypeName", "WIFI")
                        } catch (e: Throwable) {
                        }
                    } else {
                        if (isCoreSystemProcess) return@hookMethod result
                        val fakeInfo = buildFakeNetworkInfo()
                        if (fakeInfo != null) {
                            return@hookMethod fakeInfo
                        }
                    }
                }
            }
            return@hookMethod result
        }
    } catch (e: Throwable) {
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.ConnectivityManager", classLoader,
            "getNetworkCapabilities",
            "android.net.Network"
        ) { chain, method ->
            val result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig() ?: return@hookMethod result
            if (!config.optBoolean("active", false)) return@hookMethod result
            if (!config.optBoolean("mock_wifi", true)) return@hookMethod result

            val nc = result ?: return@hookMethod result
            try {
                val wifiObj = config.optJSONObject("wifi_json")
                val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
                val firstWifi =
                    if (isConnected) wifiObj.optJSONObject("connectedWifi") else null
                if (firstWifi != null) {
                    val currentInfo = try {
                        XposedHelpers.getObjectField(nc, "mTransportInfo")
                    } catch (e: Throwable) {
                        null
                    }
                    val ssidVal = firstWifi.optString("ssid", "")
                    val finalSsid =
                        if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") "HOME_WIFI" else ssidVal
                    val bssidVal = firstWifi.optString("bssid", "02:00:00:00:00:00")
                    val freqVal = firstWifi.optInt("frequency", 2412)
                    val macAddressVal = firstWifi.optString("macAddress", bssidVal)
                    val linkSpeedVal = firstWifi.optInt("linkSpeed", 65)
                    val standardVal = firstWifi.optInt("wifiStandard", 6)

                    if (currentInfo != null && currentInfo.javaClass.name.contains("WifiInfo")) {
                        try {
                            XposedHelpers.setObjectField(
                                currentInfo,
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
                                    currentInfo,
                                    "mWifiSsid",
                                    wifiSsid
                                )
                            }
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setObjectField(currentInfo, "mBSSID", bssidVal)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setObjectField(
                                currentInfo,
                                "mMacAddress",
                                macAddressVal
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(currentInfo, "mFrequency", freqVal)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(
                                currentInfo,
                                "mLinkSpeed",
                                linkSpeedVal
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(
                                currentInfo,
                                "mWifiStandard",
                                standardVal
                            )
                        } catch (e: Throwable) {
                        }
                    } else {
                        if (isCoreSystemProcess) return@hookMethod result

                        val fakeWifiInfo: Any
                        var builtWithBuilder = false
                        var builtInfo: Any? = null
                        try {
                            val builderClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiInfo\$Builder",
                                classLoader
                            )
                            val builder = XposedHelpers.newInstance(builderClass)
                            XposedHelpers.callMethod(builder, "setBssid", bssidVal)
                            try {
                                XposedHelpers.callMethod(
                                    builder,
                                    "setMacAddress",
                                    macAddressVal
                                )
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.callMethod(
                                    builder,
                                    "setSsid",
                                    finalSsid.toByteArray(Charsets.UTF_8)
                                )
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.callMethod(builder, "setNetworkId", 1)
                            } catch (e: Throwable) {
                            }
                            builtInfo = XposedHelpers.callMethod(builder, "build")

                            builtInfo?.let { info ->
                                try {
                                    XposedHelpers.setIntField(info, "mFrequency", freqVal)
                                } catch (e: Throwable) {
                                }
                                try {
                                    XposedHelpers.setIntField(
                                        info,
                                        "mLinkSpeed",
                                        linkSpeedVal
                                    )
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
                                    XposedHelpers.setIntField(
                                        info,
                                        "mWifiStandard",
                                        standardVal
                                    )
                                } catch (e: Throwable) {
                                }
                            }

                            builtWithBuilder = true
                        } catch (e: Throwable) {
                        }

                        if (builtWithBuilder && builtInfo != null) {
                            fakeWifiInfo = builtInfo
                        } else {
                            val wifiInfoClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiInfo",
                                classLoader
                            )
                            fakeWifiInfo = XposedHelpers.newInstance(wifiInfoClass)
                            try {
                                XposedHelpers.callMethod(fakeWifiInfo, "setBSSID", bssidVal)
                            } catch (e: Throwable) {
                                try {
                                    XposedHelpers.setObjectField(
                                        fakeWifiInfo,
                                        "mBSSID",
                                        bssidVal
                                    )
                                } catch (e2: Throwable) {
                                }
                                try {
                                    XposedHelpers.setObjectField(
                                        fakeWifiInfo,
                                        "mBssid",
                                        bssidVal
                                    )
                                } catch (e2: Throwable) {
                                }
                            }
                            try {
                                XposedHelpers.callMethod(
                                    fakeWifiInfo,
                                    "setMacAddress",
                                    macAddressVal
                                )
                            } catch (e: Throwable) {
                                try {
                                    XposedHelpers.setObjectField(
                                        fakeWifiInfo,
                                        "mMacAddress",
                                        macAddressVal
                                    )
                                } catch (e2: Throwable) {
                                }
                            }
                            try {
                                val wifiSsidClass = XposedHelpers.findClass(
                                    "android.net.wifi.WifiSsid",
                                    classLoader
                                )
                                val createMethod = XposedHelpers.findMethodExact(
                                    wifiSsidClass,
                                    "createFromAsciiEncoded",
                                    String::class.java
                                )
                                val wifiSsid = createMethod.invoke(null, finalSsid)
                                XposedHelpers.setObjectField(
                                    fakeWifiInfo,
                                    "mWifiSsid",
                                    wifiSsid
                                )
                            } catch (e: Throwable) {
                                try {
                                    XposedHelpers.setObjectField(
                                        fakeWifiInfo,
                                        "mSSID",
                                        "\"$finalSsid\""
                                    )
                                } catch (e2: Throwable) {
                                }
                            }
                            try {
                                XposedHelpers.setIntField(fakeWifiInfo, "mNetworkId", 1)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(
                                    fakeWifiInfo,
                                    "mFrequency",
                                    freqVal
                                )
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(
                                    fakeWifiInfo,
                                    "mLinkSpeed",
                                    linkSpeedVal
                                )
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(
                                    fakeWifiInfo,
                                    "mWifiStandard",
                                    standardVal
                                )
                            } catch (e: Throwable) {
                            }
                        }
                        XposedHelpers.setObjectField(nc, "mTransportInfo", fakeWifiInfo)
                    }

                    try {
                        val field = nc.javaClass.getDeclaredField("mTransportTypes")
                        field.isAccessible = true
                        val currentTypes = field.getLong(nc)
                        field.setLong(nc, currentTypes or (1L shl 1))
                    } catch (e: Throwable) {
                        try {
                            XposedHelpers.callMethod(nc, "addTransportType", 1)
                        } catch (e2: Throwable) {
                        }
                    }
                } else {
                    try {
                        XposedHelpers.setObjectField(nc, "mTransportInfo", null)
                    } catch (e: Throwable) {
                    }
                    XposedBridge.log("[LocationSpoofer] fakeWifiInfo: No wifi data, removed TransportInfo")
                }
            } catch (e: Throwable) {
                XposedBridge.log("[LocationSpoofer] fakeWifiInfo error: " + e.message)
            }
            return@hookMethod result
        }
    } catch (e: Throwable) {
    }

    try {
        XposedHelpers.hookMethod(
            "java.net.NetworkInterface", classLoader, "getNetworkInterfaces"
        ) { chain, method ->
            val result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig() ?: return@hookMethod result
            if (!config.optBoolean("active", false)) return@hookMethod result
            val enumResult = result as? java.util.Enumeration<*> ?: return@hookMethod result
            val filtered = java.util.Collections.list(enumResult).filter { iface ->
                val name = try {
                    (iface as java.net.NetworkInterface).name
                } catch (e: Throwable) {
                    ""
                }
                !name.startsWith("wlan") && !name.startsWith("p2p") && !name.startsWith(
                    "swlan"
                )
            }
            return@hookMethod java.util.Collections.enumeration(filtered)
        }
    } catch (e: Throwable) {
    }

    XposedBridge.log("[LocationSpoofer] Connectivity layer hooks installed")
}
