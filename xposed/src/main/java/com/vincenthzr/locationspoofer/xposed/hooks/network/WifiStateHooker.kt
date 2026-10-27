package com.vincenthzr.locationspoofer.xposed.hooks.network

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import org.json.JSONObject
import java.lang.reflect.Array as ReflectArray
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookWifiState(
    classLoader: ClassLoader,
    isCoreSystemProcess: Boolean = false
) {
    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getWifiState"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val mockWifi = config.optBoolean("mock_wifi", true)
            if (mockWifi) {
                return@hookMethod 3
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "isWifiEnabled"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val mockWifi = config.optBoolean("mock_wifi", true)
            val wifiObj = config.optJSONObject("wifi_json")
            val hasWifiData =
                wifiObj != null && (wifiObj.has("connectedWifi") || wifiObj.optJSONArray(
                    "nearbyWifi"
                )?.length() ?: 0 > 0)
            if (mockWifi) {
                return@hookMethod hasWifiData
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getConfiguredNetworks"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            return@hookMethod java.util.ArrayList<Any>()
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getDhcpInfo"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            try {
                val dhcpClass =
                    XposedHelpers.findClass("android.net.DhcpInfo", classLoader)
                val dhcp = XposedHelpers.newInstance(dhcpClass)
                XposedHelpers.setIntField(dhcp, "ipAddress", 0x6401A8C0.toInt())
                XposedHelpers.setIntField(
                    dhcp,
                    "gateway",
                    0x0101A8C0
                )
                XposedHelpers.setIntField(
                    dhcp,
                    "netmask",
                    0x00FFFFFF
                )
                XposedHelpers.setIntField(
                    dhcp,
                    "dns1",
                    0x0101A8C0
                )
                XposedHelpers.setIntField(dhcp, "dns2", 0x08080808)
                XposedHelpers.setIntField(dhcp, "serverAddress", 0x0101A8C0)
                return@hookMethod dhcp
            } catch (e: Throwable) {
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.NetworkInfo", classLoader, "getExtraInfo"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val mockWifi = config.optBoolean("mock_wifi", true)
            val wifiObj = if (mockWifi) config.optJSONObject("wifi_json") else null
            val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
            val connectedWifi =
                if (isConnected) wifiObj!!.optJSONObject("connectedWifi") else null
            if (connectedWifi != null) {
                return@hookMethod "\"${connectedWifi.optString("ssid", "HOME_WIFI")}\""
            } else {
                return@hookMethod null
            }
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.ScanResult",
            classLoader,
            "getInformationElements"
        ) { chain, _ ->
            val result = try {
                chain.proceed(chain.args.toTypedArray())
            } catch (e: Throwable) {
                null
            }
            return@hookMethod result ?: java.util.Collections.emptyList<Any>()
        }
    } catch (e: Throwable) {
    }

    if (!isCoreSystemProcess) {
        try {
            val wifiScannerClass =
                XposedHelpers.findClassIfExists("android.net.wifi.WifiScanner", classLoader)
            if (wifiScannerClass != null) {
                XposedHelpers.hookAllMethods(wifiScannerClass, "startScan") { chain, method ->
                    val config =
                        readConfig() ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    if (!config.optBoolean(
                            "active",
                            false
                        )
                    ) return@hookAllMethods chain.proceed(chain.args.toTypedArray())

                    val listener = chain.args.lastOrNull() ?: return@hookAllMethods null
                    val mockWifi = config.optBoolean("mock_wifi", true)
                    val wifiObj = if (mockWifi) config.optJSONObject("wifi_json") else null
                    if (mockWifi) {
                        try {
                            val scanResultClass = XposedHelpers.findClass(
                                "android.net.wifi.ScanResult",
                                classLoader
                            )
                            val baseTimestamp = android.os.SystemClock.elapsedRealtimeNanos()
                            val fakeList = java.util.ArrayList<Any>()

                            fun addFakeScanResult(wifi: org.json.JSONObject) {
                                val fakeScanResult = XposedHelpers.newInstance(scanResultClass)
                                val ssidVal = wifi.optString("ssid", "")
                                val bssidVal = wifi.optString("bssid", "")
                                val finalSsid =
                                    if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") {
                                        "WIFI_${bssidVal.takeLast(5).replace(":", "")}"
                                    } else {
                                        ssidVal
                                    }
                                XposedHelpers.setObjectField(fakeScanResult, "SSID", finalSsid)
                                XposedHelpers.setObjectField(fakeScanResult, "BSSID", bssidVal)
                                val level = wifi.optInt("level", -65)
                                XposedHelpers.setIntField(fakeScanResult, "level", level)
                                XposedHelpers.setIntField(
                                    fakeScanResult,
                                    "frequency",
                                    wifi.optInt("frequency", 2412)
                                )
                                XposedHelpers.setObjectField(
                                    fakeScanResult,
                                    "capabilities",
                                    wifi.optString("capabilities", "[WPA2-PSK-CCMP][ESS]")
                                )
                                try {
                                    val ieClass = XposedHelpers.findClassIfExists(
                                        "android.net.wifi.ScanResult\$InformationElement",
                                        classLoader
                                    )
                                    if (ieClass != null) {
                                        val emptyIeArray = ReflectArray.newInstance(ieClass, 0)
                                        XposedHelpers.setObjectField(fakeScanResult, "informationElements", emptyIeArray)
                                    }
                                } catch (e: Throwable) {
                                }
                                try {
                                    val rciClass = XposedHelpers.findClassIfExists(
                                        "android.net.wifi.ScanResult\$RadioChainInfo",
                                        classLoader
                                    )
                                    if (rciClass != null) {
                                        val emptyRciArray = ReflectArray.newInstance(rciClass, 0)
                                        XposedHelpers.setObjectField(fakeScanResult, "radioChainInfos", emptyRciArray)
                                    }
                                } catch (e: Throwable) {
                                }
                                try {
                                    val offsetNanos = (rng.nextInt(200_000) * 1000L)
                                    XposedHelpers.setLongField(
                                        fakeScanResult,
                                        "timestamp",
                                        (baseTimestamp - offsetNanos) / 1000
                                    )
                                } catch (e: Throwable) {
                                }
                                fakeList.add(fakeScanResult)
                            }

                        if (wifiObj != null) {
                            val isConnected = wifiObj.optBoolean("isConnected", false)
                            val connectedWifi =
                                if (isConnected) wifiObj.optJSONObject("connectedWifi") else null
                            if (connectedWifi != null) {
                                addFakeScanResult(connectedWifi)
                            }

                            val nearbyArray = wifiObj.optJSONArray("nearbyWifi")
                            if (nearbyArray != null) {
                                for (i in 0 until nearbyArray.length()) {
                                    val wifi = nearbyArray.getJSONObject(i)
                                    addFakeScanResult(wifi)
                                }
                            }
                        }

                        if (fakeList.isEmpty()) {
                            val lat = config.optDouble("lat", 0.0)
                            val lng = config.optDouble("lng", 0.0)
                            val seed = ((lat * 100000).toLong() xor (lng * 100000).toLong())
                            val random = java.util.Random(seed)
                            for (i in 0 until 5) {
                                val fakeWifi = org.json.JSONObject()
                                fakeWifi.put("ssid", "WIFI_${random.nextInt(9000) + 1000}")
                                val bssid = String.format(
                                    "%02x:%02x:%02x:%02x:%02x:%02x",
                                    random.nextInt(256),
                                    random.nextInt(256),
                                    random.nextInt(256),
                                    random.nextInt(256),
                                    random.nextInt(256),
                                    random.nextInt(256)
                                )
                                fakeWifi.put("bssid", bssid)
                                fakeWifi.put("level", -40 - random.nextInt(50))
                                fakeWifi.put(
                                    "frequency",
                                    if (random.nextBoolean()) 2412 else 5180
                                )
                                fakeWifi.put("capabilities", "[WPA2-PSK-CCMP][ESS]")
                                addFakeScanResult(fakeWifi)
                            }
                        }

                        if (fakeList.isNotEmpty()) {
                            val scanResultArray = ReflectArray.newInstance(
                                scanResultClass,
                                fakeList.size
                            )
                            for (i in 0 until fakeList.size) {
                                ReflectArray.set(scanResultArray, i, fakeList[i])
                            }

                            val scanDataClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiScanner\$ScanData",
                                classLoader
                            )
                            val fakeScanData = XposedHelpers.newInstance(
                                scanDataClass,
                                0,
                                0,
                                scanResultArray
                            )
                            val fakeScanDataArray =
                                ReflectArray.newInstance(scanDataClass, 1)
                            ReflectArray.set(fakeScanDataArray, 0, fakeScanData)

                            XposedHelpers.callMethod(
                                listener,
                                "onResults",
                                fakeScanDataArray
                            )
                        } else {
                            val scanDataClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiScanner\$ScanData",
                                classLoader
                            )
                            val emptyScanData = XposedHelpers.newInstance(
                                scanDataClass,
                                0,
                                0,
                                ReflectArray.newInstance(scanResultClass, 0)
                            )
                            val fakeScanDataArray =
                                ReflectArray.newInstance(scanDataClass, 1)
                            ReflectArray.set(fakeScanDataArray, 0, emptyScanData)
                            XposedHelpers.callMethod(
                                listener,
                                "onResults",
                                fakeScanDataArray
                            )
                        }
                    } catch (e: Throwable) {
                        XposedBridge.log("[LocationSpoofer] WifiScanner 伪造失败: $e")
                    }
                } else {
                    try {
                        val scanDataClass = XposedHelpers.findClass(
                            "android.net.wifi.WifiScanner\$ScanData",
                            classLoader
                        )
                        val scanResultClass = XposedHelpers.findClass(
                            "android.net.wifi.ScanResult",
                            classLoader
                        )
                        val emptyScanData = XposedHelpers.newInstance(
                            scanDataClass,
                            0,
                            0,
                            ReflectArray.newInstance(scanResultClass, 0)
                        )
                        val fakeScanDataArray =
                            ReflectArray.newInstance(scanDataClass, 1)
                        ReflectArray.set(fakeScanDataArray, 0, emptyScanData)
                        XposedHelpers.callMethod(listener, "onResults", fakeScanDataArray)
                    } catch (e: Throwable) {
                    }
                }
                return@hookAllMethods null
            }
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }
}

    XposedBridge.log("[LocationSpoofer] Wi-Fi environment hooks installed")
}
