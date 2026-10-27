package com.vincenthzr.locationspoofer.xposed.hooks.network

import com.vincenthzr.locationspoofer.xposed.utils.*
import io.github.libxposed.api.*

internal fun hexStringToByteArray(s: String): ByteArray {
    val len = s.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] =
            ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

internal fun normalizeMacAddress(mac: String): String {
        val clean = mac.replace(":", "").replace("-", "").trim().uppercase()
        if (clean.length == 12 && clean.all { it in "0123456789ABCDEF" }) {
            return clean.chunked(2).joinToString(":")
        }
        return mac.trim().uppercase()
    }

internal fun isValidAdStructure(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        var pos = 0
        var foundValidAd = false
        while (pos < bytes.size) {
            val len = bytes[pos].toInt() and 0xFF
            if (len == 0) {
                return foundValidAd
            }
            if (pos + 1 + len > bytes.size) {
                return false
            }
            foundValidAd = true
            pos += 1 + len
        }
        return foundValidAd
    }

internal fun buildScanRecordBytes(name: String, hexRecord: String, address: String = ""): ByteArray {
        val cleanHex = hexRecord.replace("0x", "", ignoreCase = true)
            .replace("0X", "")
            .replace(" ", "")
            .replace(":", "")
            .replace("-", "")
            .replace(",", "")
            .replace(";", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
            .uppercase()

        var rawPayload: ByteArray? = null

        if (cleanHex.isNotEmpty() && cleanHex.length >= 2) {
            try {
                val validHex = if (cleanHex.length % 2 != 0) cleanHex.substring(0, cleanHex.length - 1) else cleanHex
                val inputBytes = hexStringToByteArray(validHex)

                if (isValidAdStructure(inputBytes)) {
                    rawPayload = inputBytes
                } else if (validHex.startsWith("1AFF4C00") || validHex.startsWith("1EFF4C00")) {
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    rawPayload = flags + inputBytes
                } else if (validHex.startsWith("FF4C00") || validHex.startsWith("FF")) {
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val lenByte = byteArrayOf(inputBytes.size.toByte())
                    rawPayload = flags + lenByte + inputBytes
                } else if (validHex.startsWith("4C000215") || validHex.startsWith("004C0215") || validHex.startsWith("4C00")) {
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val mfrHeader = byteArrayOf((inputBytes.size + 1).toByte(), 0xFF.toByte())
                    rawPayload = flags + mfrHeader + inputBytes
                } else if (validHex.startsWith("0215") && validHex.length >= 46) {
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val mfrHeader = byteArrayOf(0x1A, 0xFF.toByte(), 0x4C, 0x00)
                    rawPayload = flags + mfrHeader + inputBytes
                } else if (validHex.length == 32) {
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val mfrData = byteArrayOf(
                        0x1A, 0xFF.toByte(), 0x4C, 0x00, 0x02, 0x15
                    ) + inputBytes + byteArrayOf(0x00, 0x01, 0x00, 0x01, 0xC5.toByte())
                    rawPayload = flags + mfrData
                } else {
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val mfrHeader = byteArrayOf((inputBytes.size + 1).toByte(), 0xFF.toByte())
                    rawPayload = flags + mfrHeader + inputBytes
                }
            } catch (_: Throwable) {}
        }

        if (rawPayload != null && rawPayload.isNotEmpty()) {
            return rawPayload
        }

        val flags = byteArrayOf(0x02, 0x01, 0x06)
        val defaultIBeacon = byteArrayOf(
            0x1A, 0xFF.toByte(), 0x4C, 0x00, 0x02, 0x15,
            0xFD.toByte(), 0xA5.toByte(), 0x06, 0x93.toByte(),
            0xA4.toByte(), 0xE2.toByte(), 0x4F, 0xB1.toByte(),
            0xAF.toByte(), 0xCF.toByte(), 0xC6.toByte(), 0xEB.toByte(),
            0x07, 0x64, 0x78, 0x25,
            0x00, 0x01, 0x00, 0x01, 0xC5.toByte()
        )
        var synthetic = flags + defaultIBeacon
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        if (nameBytes.isNotEmpty() && synthetic.size + nameBytes.size + 2 <= 31) {
            val nameAd = byteArrayOf((nameBytes.size + 1).toByte(), 0x09) + nameBytes
            synthetic = synthetic + nameAd
        }

        return synthetic
    }

internal fun createBluetoothDevice(cl: ClassLoader, address: String): Any? {
        val cleanAddress = normalizeMacAddress(address)
        val devClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothDevice", cl) ?: return null
        
        try {
            val adapterClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothAdapter", cl)
            if (adapterClass != null) {
                val adapter = XposedHelpers.callStaticMethod(adapterClass, "getDefaultAdapter")
                if (adapter != null) {
                    val dev = XposedHelpers.callMethod(adapter, "getRemoteDevice", cleanAddress)
                    if (dev != null) return dev
                }
            }
        } catch (_: Throwable) {}

        try {
            val ctor = devClass.getDeclaredConstructor(String::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(cleanAddress)
        } catch (_: Throwable) {}

        for (c in devClass.declaredConstructors) {
            try {
                if (c.parameterTypes.size == 1 && c.parameterTypes[0] == String::class.java) {
                    c.isAccessible = true
                    return c.newInstance(cleanAddress)
                }
            } catch (_: Throwable) {}
        }
        return null
    }

internal fun createScanResult(
        cl: ClassLoader,
        device: Any,
        scanRecord: Any?,
        rssi: Int,
        timestampNanos: Long
    ): Any? {
        val scanResultClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanResult", cl) ?: return null
        val devClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothDevice", cl) ?: return null
        val recordClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanRecord", cl)

        var result: Any? = null

        if (recordClass != null) {
            try {
                val ctor10 = scanResultClass.getDeclaredConstructor(
                    devClass,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    recordClass,
                    Long::class.javaPrimitiveType
                )
                result = ctor10.newInstance(
                    device,
                    0x001B,
                    1,
                    0,
                    255,
                    127,
                    rssi,
                    0,
                    scanRecord,
                    timestampNanos
                )
            } catch (_: Throwable) {}
        }

        if (result == null && recordClass != null) {
            try {
                val ctor4 = scanResultClass.getDeclaredConstructor(
                    devClass, recordClass, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType
                )
                ctor4.isAccessible = true
                result = ctor4.newInstance(device, scanRecord, rssi, timestampNanos)
            } catch (_: Throwable) {}
        }

        if (result == null) {
            for (c in scanResultClass.declaredConstructors) {
                try {
                    c.isAccessible = true
                    val args = arrayOfNulls<Any>(c.parameterCount)
                    for (idx in 0 until c.parameterCount) {
                        val pType = c.parameterTypes[idx]
                        when {
                            devClass.isAssignableFrom(pType) -> args[idx] = device
                            recordClass != null && recordClass.isAssignableFrom(pType) -> args[idx] = scanRecord
                            pType == Int::class.javaPrimitiveType || pType == java.lang.Integer::class.java -> {
                                when (idx) {
                                    1 -> args[idx] = 0x001B
                                    2 -> args[idx] = if (c.parameterCount == 4) rssi else 1
                                    3 -> args[idx] = 0
                                    4 -> args[idx] = 255
                                    5 -> args[idx] = 127
                                    6 -> args[idx] = rssi
                                    else -> args[idx] = if (idx == c.parameterCount - 2) rssi else 0
                                }
                            }
                            pType == Long::class.javaPrimitiveType || pType == java.lang.Long::class.java -> args[idx] = timestampNanos
                            pType == Boolean::class.javaPrimitiveType || pType == java.lang.Boolean::class.java -> args[idx] = false
                            else -> args[idx] = null
                        }
                    }
                    result = c.newInstance(*args)
                    if (result != null) break
                } catch (_: Throwable) {}
            }
        }

        if (result != null) {
            try { XposedHelpers.setObjectField(result, "mDevice", device) } catch (_: Throwable) {}
            try { XposedHelpers.setObjectField(result, "mScanRecord", scanRecord) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(result, "mRssi", rssi) } catch (_: Throwable) {}
            try { XposedHelpers.setLongField(result, "mTimestampNanos", timestampNanos) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(result, "mEventType", 0x001B) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(result, "mPrimaryPhy", 1) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(result, "mTxPower", 127) } catch (_: Throwable) {}
        }

        return result
    }
