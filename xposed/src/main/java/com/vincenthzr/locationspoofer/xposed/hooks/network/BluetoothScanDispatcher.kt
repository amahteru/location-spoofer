package com.vincenthzr.locationspoofer.xposed.hooks.network

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import org.json.JSONObject
import kotlin.random.Random
import io.github.libxposed.api.*

// 辅助函数：安全反射调用 onScanResult
internal fun LocationHooker.dispatchScanResultToCallback(callback: Any, scanResultObj: Any) {
        var dispatched = false
        var c: Class<*>? = callback.javaClass
        while (c != null && c != Any::class.java) {
            for (m in c.declaredMethods) {
                if (m.name == "onScanResult") {
                    if (m.parameterCount == 2) {
                        try {
                            m.isAccessible = true
                            m.invoke(callback, 1 /* CALLBACK_TYPE_ALL_MATCHES */, scanResultObj)
                            dispatched = true
                            break
                        } catch (e: Throwable) {
                            XposedBridge.log("[LocationSpoofer] dispatch onScanResult (2-args) direct error: ${e.cause ?: e}")
                        }
                    } else if (m.parameterCount == 1) {
                        try {
                            m.isAccessible = true
                            m.invoke(callback, scanResultObj)
                            dispatched = true
                            break
                        } catch (e: Throwable) {
                            XposedBridge.log("[LocationSpoofer] dispatch onScanResult (1-arg) direct error: ${e.cause ?: e}")
                        }
                    }
                }
            }
            if (dispatched) break
            c = c.superclass
        }
        if (!dispatched) {
            try {
                XposedHelpers.callMethod(callback, "onScanResult", 1, scanResultObj)
                dispatched = true
            } catch (_: Throwable) {
                try {
                    XposedHelpers.callMethod(callback, "onScanResult", scanResultObj)
                    dispatched = true
                } catch (_: Throwable) {}
            }
        }
        if (!dispatched) {
            try {
                val inner = XposedHelpers.getObjectField(callback, "mScanCallback") ?:
                            XposedHelpers.getObjectField(callback, "mCallback")
                if (inner != null) {
                    dispatchScanResultToCallback(inner, scanResultObj)
                }
            } catch (_: Throwable) {}
        }
    }

// 辅助函数：安全反射调用 onBatchScanResults
internal fun LocationHooker.dispatchBatchResultsToCallback(callback: Any, results: List<Any>) {
        var dispatched = false
        var c: Class<*>? = callback.javaClass
        while (c != null && c != Any::class.java) {
            for (m in c.declaredMethods) {
                if (m.name == "onBatchScanResults" && m.parameterCount == 1) {
                    try {
                        m.isAccessible = true
                        m.invoke(callback, results)
                        dispatched = true
                        break
                    } catch (_: Throwable) {}
                }
            }
            if (dispatched) break
            c = c.superclass
        }
        if (!dispatched) {
            try {
                XposedHelpers.callMethod(callback, "onBatchScanResults", results)
            } catch (_: Throwable) {}
        }
    }

// BLE 扫描结果伪造的核心分发逻辑
internal val syntheticBleDelivery = com.vincenthzr.locationspoofer.xposed.utils.CallScope<Boolean>()

internal fun LocationHooker.deliverBleScanResults(config: JSONObject, callback: Any, cl: ClassLoader) {
        val mainHandler = Handler(Looper.getMainLooper())
        val scanRecordClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanRecord", cl)
        if (!config.optBoolean("mock_bluetooth", true)) return
        val bluetoothArray = config.optJSONArray("bluetooth_json") ?: return
        if (bluetoothArray.length() == 0) return

        val results = java.util.ArrayList<Any>()
        val targetScanRecordClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanRecord", cl) ?: scanRecordClass
        val timestampNanos = SystemClock.elapsedRealtimeNanos()

        for (i in 0 until bluetoothArray.length()) {
            try {
                val obj = bluetoothArray.getJSONObject(i)
                val address = obj.optString("address", "00:11:22:33:44:55")
                val name = obj.optString("name", "")
                val baseRssi = obj.optInt("rssi", -60)
                val rssi = baseRssi + (if (config.optBoolean("enable_jitter", true)) Random.nextInt(-2, 3) else 0)
                val hexRecord = obj.optString("scanRecordHex", "")
                val rawBytes = buildScanRecordBytes(name, hexRecord, address)

                val device = createBluetoothDevice(cl, address)
                if (device == null) {
                    XposedBridge.log("[LocationSpoofer] 创建虚拟蓝牙设备失败 address=$address")
                    continue
                }

                var scanRecord: Any? = null
                if (targetScanRecordClass != null && rawBytes.isNotEmpty()) {
                    try {
                        scanRecord = XposedHelpers.callStaticMethod(targetScanRecordClass, "parseFromBytes", rawBytes)
                    } catch (e: Throwable) {
                        XposedBridge.log("[LocationSpoofer] ScanRecord.parseFromBytes 异常: $e")
                    }
                }

                val scanResultObj = createScanResult(cl, device, scanRecord, rssi, timestampNanos)
                if (scanResultObj != null) {
                    results.add(scanResultObj)
                    mainHandler.post {
                        try {
                            syntheticBleDelivery.withValue(true) { dispatchScanResultToCallback(callback, scanResultObj) }
                            XposedBridge.log("[LocationSpoofer] 成功派发 onScanResult 给 ${callback.javaClass.name} | MAC=$address RSSI=$rssi 广播包长=${rawBytes.size}")
                        } catch (e: Throwable) {
                            XposedBridge.log("[LocationSpoofer] 派发 onScanResult 异常: $e")
                        }
                    }
                } else {
                    XposedBridge.log("[LocationSpoofer] 创建 ScanResult 失败 address=$address")
                }
            } catch (e: Throwable) {
                XposedBridge.log("[LocationSpoofer] 构造虚拟BLE失败: $e")
            }
        }
        if (results.isNotEmpty()) {
            mainHandler.post {
                try {
                    syntheticBleDelivery.withValue(true) { dispatchBatchResultsToCallback(callback, results) }
                } catch (_: Throwable) {}
            }
        }
    }
