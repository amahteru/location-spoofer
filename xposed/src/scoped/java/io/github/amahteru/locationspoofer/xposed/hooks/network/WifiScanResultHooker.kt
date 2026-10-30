package io.github.amahteru.locationspoofer.xposed.hooks.network

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import org.json.JSONObject
import java.lang.reflect.Array as ReflectArray
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.hookWifiEnvironment(
    classLoader: ClassLoader,
    isCoreSystemProcess: Boolean = false
) {
    hookWifiScanResults(classLoader)
    hookWifiConnectionInfo(classLoader, isCoreSystemProcess)
    hookWifiState(classLoader, isCoreSystemProcess)
}

internal fun LocationHooker.hookWifiScanResults(classLoader: ClassLoader) {
    val realCapabilities = listOf(
        "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]",
        "[WPA2-PSK-CCMP+TKIP][RSN-PSK-CCMP+TKIP][ESS]",
        "[WPA2-PSK-CCMP][ESS][WPS]",
        "[WPA-PSK-TKIP+CCMP][WPA2-PSK-TKIP+CCMP][ESS]",
        "[RSN-PSK-CCMP][ESS]",
        "[WPA2-EAP-CCMP][RSN-EAP-CCMP][ESS]",
        "[ESS]",
        "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]",
        "[WPA2-SAE-CCMP][RSN-SAE-CCMP][ESS]",
        "[WPA2-PSK+SAE-CCMP][RSN-PSK+SAE-CCMP][ESS]"
    )

    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getScanResults"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean("active", false) || !config.optBoolean("mock_wifi", true)) {
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
            val fakeList = java.util.ArrayList<Any>()
            val wifiObj = config.optJSONObject("wifi_json")
            try {
                    val scanResultClass =
                        XposedHelpers.findClass("android.net.wifi.ScanResult", classLoader)
                    val baseTimestamp = android.os.SystemClock.elapsedRealtimeNanos()

                    fun addFakeScanResult(wifi: org.json.JSONObject) {
                        val fakeScanResult = XposedHelpers.newInstance(scanResultClass)
                        val ssidVal = wifi.optString("ssid", "")
                        val bssidVal = wifi.optString("bssid", "")
                        val finalSsid = if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") {
                            "WIFI_${bssidVal.takeLast(5).replace(":", "")}"
                        } else {
                            ssidVal
                        }
                        XposedHelpers.setObjectField(fakeScanResult, "SSID", finalSsid)
                        XposedHelpers.setObjectField(fakeScanResult, "BSSID", bssidVal)
                        val level = wifi.optInt("level", -65)
                        XposedHelpers.setIntField(fakeScanResult, "level", level)
                        XposedHelpers.setIntField(
                            fakeScanResult, "frequency",
                            wifi.optInt("frequency", 2412)
                        )
                        XposedHelpers.setObjectField(
                            fakeScanResult, "capabilities",
                            wifi.optString("capabilities", realCapabilities.random())
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
                                fakeScanResult, "timestamp",
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
                                random.nextInt(256), random.nextInt(256), random.nextInt(256),
                                random.nextInt(256), random.nextInt(256), random.nextInt(256)
                            )
                            fakeWifi.put("bssid", bssid)
                            fakeWifi.put("level", -40 - random.nextInt(50))
                            fakeWifi.put("frequency", if (random.nextBoolean()) 2412 else 5180)
                            fakeWifi.put("capabilities", "[WPA2-PSK-CCMP][ESS]")
                            addFakeScanResult(fakeWifi)
                        }
                    }
                } catch (e: Throwable) {
                }
            return@hookMethod fakeList
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }
}
