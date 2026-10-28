package io.github.amahteru.locationspoofer.xposed.hooks.network

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.*
import io.github.amahteru.locationspoofer.xposed.hooks.*
import org.json.JSONObject
import kotlin.math.*
import io.github.libxposed.api.*

internal fun LocationHooker.buildFakeCellInfoList(
    classLoader: ClassLoader, lat: Double, lng: Double, config: org.json.JSONObject?
): java.util.ArrayList<Any> {
    val result = java.util.ArrayList<Any>()

    val cellArray = config?.optJSONArray("cell_json")
    XposedBridge.logOpenCellIdEvery(
        "buildFakeCellInfoList:called:${cellArray?.length() ?: 0}",
        "buildFakeCellInfoList called cellJsonCount=${cellArray?.length() ?: 0}"
    )
    if (cellArray != null && cellArray.length() > 0) {
        var hasLteOrNr = false
        var gsmCount = 0
        var wcdmaCount = 0
        var lteCount = 0
        var nrCount = 0
        var firstSummary: String? = null
        for (i in 0 until cellArray.length()) {
            try {
                val obj = cellArray.getJSONObject(i)
                val type =
                    normalizeCellType(obj.optString("type", obj.optString("radio", "LTE")))
                val isRegistered = obj.optBoolean("isRegistered", i == 0)
                if (type == "LTE" || type == "NR") {
                    hasLteOrNr = true
                }
                when (type) {
                    "GSM" -> gsmCount++
                    "WCDMA", "UMTS" -> wcdmaCount++
                    "NR" -> nrCount++
                    else -> lteCount++
                }

                val mcc = positiveJsonInt(obj, "mcc", default = 460)
                val mnc = positiveJsonInt(obj, "mnc", "net", default = 0)
                val tacOrLac = cellAreaCode(obj, 10000)
                val ciOrCid = cellIdentityCode(obj, 100000)
                val pci = positiveJsonInt(
                    obj,
                    "pci",
                    "psc",
                    default = (ciOrCid % 504).coerceIn(0, 503)
                )
                val dbm = signalDbm(obj, i)
                if (firstSummary == null) {
                    firstSummary =
                        "$type/$mcc-$mnc area=$tacOrLac identity=$ciOrCid dbm=$dbm registered=$isRegistered"
                }
                if (LocationHooker.VERBOSE_CELL_BUILD_LOGS) {
                    XposedBridge.logOpenCellId(
                        "buildFakeCellInfoList source[$i] radio=${
                            obj.optString(
                                "radio",
                                ""
                            )
                        } type=$type registered=$isRegistered mcc=$mcc mnc=$mnc area=$tacOrLac identity=$ciOrCid pci=$pci dbm=$dbm"
                    )
                }

                val cellInfoClass = when (type) {
                    "GSM" -> XposedHelpers.findClass(
                        "android.telephony.CellInfoGsm",
                        classLoader
                    )

                    "WCDMA", "UMTS" -> XposedHelpers.findClass(
                        "android.telephony.CellInfoWcdma",
                        classLoader
                    )

                    "NR" -> try {
                        XposedHelpers.findClass("android.telephony.CellInfoNr", classLoader)
                    } catch (e: Throwable) {
                        XposedHelpers.findClass("android.telephony.CellInfoLte", classLoader)
                    }

                    else -> XposedHelpers.findClass(
                        "android.telephony.CellInfoLte",
                        classLoader
                    )
                }
                val cellInfo = XposedHelpers.newInstance(cellInfoClass)

                val connectionStatus = if (isRegistered) 1 else 0
                try {
                    XposedHelpers.setBooleanField(cellInfo, "mRegistered", isRegistered)
                } catch (_: Throwable) {
                }
                try {
                    XposedHelpers.setIntField(
                        cellInfo,
                        "mCellConnectionStatus",
                        connectionStatus
                    )
                } catch (_: Throwable) {
                }
                try {
                    if (isRegistered) XposedHelpers.callMethod(cellInfo, "setRegistered", true)
                } catch (_: Throwable) {
                }

                try {
                    XposedHelpers.setLongField(
                        cellInfo,
                        "mTimeStamp",
                        android.os.SystemClock.elapsedRealtimeNanos()
                    )
                } catch (e: Throwable) {
                }

                val cellIdentityClass = when (type) {
                    "GSM" -> XposedHelpers.findClass(
                        "android.telephony.CellIdentityGsm",
                        classLoader
                    )

                    "WCDMA", "UMTS" -> XposedHelpers.findClass(
                        "android.telephony.CellIdentityWcdma",
                        classLoader
                    )

                    "NR" -> try {
                        XposedHelpers.findClass("android.telephony.CellIdentityNr", classLoader)
                    } catch (e: Throwable) {
                        XposedHelpers.findClass(
                            "android.telephony.CellIdentityLte",
                            classLoader
                        )
                    }

                    else -> XposedHelpers.findClass(
                        "android.telephony.CellIdentityLte",
                        classLoader
                    )
                }
                val mccStr = mcc.toString()
                val mncStr = if (mnc < 10) "0$mnc" else mnc.toString()
                val cellIdentity = constructCellIdentityByType(
                    type, cellIdentityClass, mcc, mccStr, mnc, mncStr, tacOrLac, ciOrCid, pci
                )
                if (LocationHooker.VERBOSE_CELL_BUILD_LOGS) {
                    XposedBridge.logOpenCellId("Built $type identity: MCC=$mcc MNC=$mnc TAC/LAC=$tacOrLac CI/CID=$ciOrCid PCI=$pci -> ${cellIdentity.javaClass.simpleName}")
                }

                try {
                    val verifyMethod = when (type) {
                        "LTE" -> "getCi"
                        "GSM" -> "getLac"
                        "WCDMA", "UMTS" -> "getLac"
                        "NR" -> "getPci"
                        else -> "getCi"
                    }
                    val readBack = XposedHelpers.callMethod(cellIdentity, verifyMethod) as? Int
                    if (readBack == Int.MAX_VALUE || readBack == -1) {
                        XposedBridge.logOpenCellId("WARNING: $type.$verifyMethod()=$readBack, identity injection may have failed")
                    } else if (LocationHooker.VERBOSE_CELL_BUILD_LOGS) {
                        XposedBridge.logOpenCellId("VERIFY OK: $type.$verifyMethod()=$readBack")
                    }
                } catch (_: Throwable) {
                }

                val identityField = when (type) {
                    "GSM" -> "mCellIdentityGsm"
                    "WCDMA", "UMTS" -> "mCellIdentityWcdma"
                    "NR" -> "mCellIdentityNr"
                    else -> "mCellIdentityLte"
                }
                try {
                    XposedHelpers.setObjectField(cellInfo, identityField, cellIdentity)
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.setObjectField(cellInfo, "mCellIdentity", cellIdentity)
                } catch (e: Throwable) {
                }

                val cssClass = when (type) {
                    "GSM" -> XposedHelpers.findClass(
                        "android.telephony.CellSignalStrengthGsm",
                        classLoader
                    )

                    "WCDMA", "UMTS" -> XposedHelpers.findClass(
                        "android.telephony.CellSignalStrengthWcdma",
                        classLoader
                    )

                    "NR" -> try {
                        XposedHelpers.findClass(
                            "android.telephony.CellSignalStrengthNr",
                            classLoader
                        )
                    } catch (e: Throwable) {
                        XposedHelpers.findClass(
                            "android.telephony.CellSignalStrengthLte",
                            classLoader
                        )
                    }

                    else -> XposedHelpers.findClass(
                        "android.telephony.CellSignalStrengthLte",
                        classLoader
                    )
                }
                val css = XposedHelpers.newInstance(cssClass)

                when (type) {
                    "GSM" -> {
                        val asu = ((dbm + 113) / 2).coerceIn(0, 31)
                        try {
                            XposedHelpers.setIntField(css, "mRssi", dbm)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mGsmSignalStrength", asu)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mSignalStrength", dbm)
                        } catch (e: Throwable) {
                        }
                    }

                    "WCDMA", "UMTS" -> {
                        val asu = (dbm + 116).coerceIn(0, 95)
                        try {
                            XposedHelpers.setIntField(css, "mRscp", dbm)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mSignalStrength", dbm)
                        } catch (e: Throwable) {
                        }
                    }

                    "NR" -> {
                        try {
                            XposedHelpers.setIntField(css, "mCsiRsrp", dbm)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mCsiRsrq", -10)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mCsiSinr", 15)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mSsRsrp", dbm)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mSsRsrq", -10)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mSsSinr", 15)
                        } catch (e: Throwable) {
                        }
                    }

                    else -> {
                        try {
                            XposedHelpers.setIntField(css, "mRsrp", dbm)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mRsrq", -10)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mRssnr", 300)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(css, "mSignalStrength", dbm + 113)
                        } catch (e: Throwable) {
                        }
                    }
                }

                val cssField = when (type) {
                    "GSM" -> "mCellSignalStrengthGsm"
                    "WCDMA", "UMTS" -> "mCellSignalStrengthWcdma"
                    "NR" -> "mCellSignalStrengthNr"
                    else -> "mCellSignalStrengthLte"
                }
                try {
                    XposedHelpers.setObjectField(cellInfo, cssField, css)
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.setObjectField(cellInfo, "mCellSignalStrength", css)
                } catch (e: Throwable) {
                }

                result.add(cellInfo)
            } catch (e: Throwable) {
                XposedBridge.logOpenCellId(
                    "buildFakeCellInfoList failed to parse/build cell_json[$i]",
                    e
                )
            }
        }
        if (result.isNotEmpty() && !hasLteOrNr) {
            try {
                val seed = org.json.JSONObject(cellArray.getJSONObject(0).toString()).apply {
                    put("type", "LTE")
                    put("radio", "LTE")
                    put("isRegistered", true)
                }
                val syntheticConfig = org.json.JSONObject().put(
                    "cell_json",
                    org.json.JSONArray().put(seed)
                )
                val syntheticLte = buildFakeCellInfoList(classLoader, lat, lng, syntheticConfig)
                if (syntheticLte.isNotEmpty()) {
                    result.add(0, syntheticLte[0])
                    XposedBridge.logOpenCellIdEvery(
                        "buildFakeCellInfoList:synthetic-lte",
                        "OpenCellID data has no LTE/NR cells; prepended synthetic LTE primary cell for 4G-only readers",
                        60_000L
                    )
                }
            } catch (e: Throwable) {
                XposedBridge.logOpenCellId("Failed to prepend synthetic LTE primary cell", e)
            }
        }
        XposedBridge.logOpenCellIdEvery(
            "buildFakeCellInfoList:return:$lteCount:$nrCount:$wcdmaCount:$gsmCount:${result.size}:$firstSummary",
            "buildFakeCellInfoList returning ${result.size} cells from cell_json types=LTE:$lteCount NR:$nrCount WCDMA:$wcdmaCount GSM:$gsmCount first=$firstSummary"
        )
        return result
    }

    val coordSeed = ((lat * 1e5).toLong() xor (lng * 1e5).toLong())
    XposedBridge.logOpenCellIdEvery(
        "buildFakeCellInfoList:fallback:$coordSeed",
        "buildFakeCellInfoList has no cell_json; generating deterministic LTE fallback cells seed=$coordSeed",
        60_000L
    )

    val operators = listOf(
        Pair(460, 0),
        Pair(460, 1),
        Pair(460, 11)
    )

    val cellCount = 2 + (coordSeed and 1).toInt()
    for (i in 0 until cellCount) {
        try {
            val mcc = operators[i % operators.size].first
            val mnc = operators[i % operators.size].second
            val tac = (10000 + ((coordSeed + i * 7919) and 0xFFFF).toInt() % 50000)
                .coerceIn(1, 65534)
            val ci = (100000 + (((coordSeed shr 8) + i * 104729) and 0xFFFFFF).toInt() % 900000)
                .coerceIn(1, 268435455)
            val pci = (coordSeed + i * 31).toInt() and 0x1FF

            val cellIdentityLteClass = XposedHelpers.findClass(
                "android.telephony.CellIdentityLte", classLoader
            )
            val cellInfoLteClass = XposedHelpers.findClass(
                "android.telephony.CellInfoLte", classLoader
            )

            val cellInfo = XposedHelpers.newInstance(cellInfoLteClass)

            try {
                XposedHelpers.setBooleanField(cellInfo, "mRegistered", i == 0)
            } catch (e: Throwable) {
                try {
                    XposedHelpers.callMethod(cellInfo, "setRegistered", i == 0)
                } catch (e2: Throwable) {
                }
            }

            try {
                XposedHelpers.setLongField(
                    cellInfo, "mTimeStamp",
                    android.os.SystemClock.elapsedRealtimeNanos()
                )
            } catch (e: Throwable) {
            }

            val mccStr = mcc.toString()
            val mncStr = if (mnc < 10) "0$mnc" else mnc.toString()
            val cellIdentity = constructCellIdentityByType(
                "LTE", cellIdentityLteClass, mcc, mccStr, mnc, mncStr, tac, ci, pci
            )

            try {
                XposedHelpers.setObjectField(cellInfo, "mCellIdentityLte", cellIdentity)
            } catch (e: Throwable) {
            }

            try {
                val cssClass = XposedHelpers.findClass(
                    "android.telephony.CellSignalStrengthLte", classLoader
                )
                val css = XposedHelpers.newInstance(cssClass)
                val rsrp = -80 - rng.nextInt(20)
                val rsrq = -10 - rng.nextInt(7)
                val rssi = -70 - rng.nextInt(20)
                try {
                    XposedHelpers.setIntField(css, "mRsrp", rsrp)
                } catch (e2: Throwable) {
                }
                try {
                    XposedHelpers.setIntField(css, "mRsrq", rsrq)
                } catch (e2: Throwable) {
                }
                try {
                    XposedHelpers.setIntField(css, "mSignalStrength", rssi)
                } catch (e2: Throwable) {
                }
                XposedHelpers.setObjectField(cellInfo, "mCellSignalStrengthLte", css)
            } catch (e: Throwable) {
            }

            result.add(cellInfo)
            if (LocationHooker.VERBOSE_CELL_BUILD_LOGS) {
                XposedBridge.logOpenCellId("Fallback LTE cell[$i] built mcc=$mcc mnc=$mnc tac=$tac ci=$ci pci=$pci registered=${i == 0}")
            }
        } catch (e: Throwable) {
            XposedBridge.logOpenCellId("Fallback LTE cell[$i] build failed", e)
        }
    }
    XposedBridge.logOpenCellIdEvery(
        "buildFakeCellInfoList:fallback-return:${result.size}",
        "buildFakeCellInfoList returning ${result.size} fallback LTE cells",
        60_000L
    )
    return result
}

internal fun LocationHooker.normalizeCellType(rawType: String): String {
    return when (rawType.uppercase(java.util.Locale.US)) {
        "GSM" -> "GSM"
        "UMTS", "WCDMA" -> "WCDMA"
        "NR", "NR5G", "5G" -> "NR"
        else -> "LTE"
    }
}

internal fun LocationHooker.cellAreaCode(cell: org.json.JSONObject, default: Int): Int =
    positiveJsonInt(cell, "tac", "lac", "area", default = default)

internal fun LocationHooker.cellIdentityCode(cell: org.json.JSONObject, default: Int): Int =
    positiveJsonInt(cell, "ci", "cid", "cellid", "cell", default = default)

internal fun registeredCellJson(cellArray: org.json.JSONArray?): org.json.JSONObject? {
    if (cellArray == null || cellArray.length() == 0) return null
    for (i in 0 until cellArray.length()) {
        val obj = cellArray.optJSONObject(i) ?: continue
        if (obj.optBoolean("isRegistered", i == 0)) return obj
    }
    return cellArray.optJSONObject(0)
}

internal fun LocationHooker.registeredCellCodes(config: org.json.JSONObject, lat: Double, lng: Double): Pair<Int, Int> {
    val cell = registeredCellJson(config.optJSONArray("cell_json"))
        ?: return fallbackAreaCode(lat, lng) to fallbackCellIdentity(lat, lng)
    return cellAreaCode(cell, 10000) to cellIdentityCode(cell, 100000)
}

internal fun LocationHooker.fallbackAreaCode(lat: Double, lng: Double): Int {
    val coordSeed = ((lat * 1e5).toLong() xor (lng * 1e5).toLong())
    return (10000 + (coordSeed and 0xFFFF).toInt() % 50000).coerceIn(1, 65534)
}

internal fun LocationHooker.fallbackCellIdentity(lat: Double, lng: Double): Int {
    val coordSeed = ((lat * 1e5).toLong() xor (lng * 1e5).toLong())
    return (100000 + ((coordSeed shr 8) and 0xFFFFFF).toInt() % 900000)
        .coerceIn(1, 268435455)
}

internal fun LocationHooker.positiveJsonInt(
    cell: org.json.JSONObject,
    vararg keys: String,
    default: Int
): Int {
    for (key in keys) {
        if (!cell.has(key) || cell.isNull(key)) continue
        val value = cell.optInt(key, Int.MIN_VALUE)
        if (value > 0) return value
        val parsed = cell.optString(key).toIntOrNull()
        if (parsed != null && parsed > 0) return parsed
    }
    return default
}

internal fun LocationHooker.signalDbm(cell: org.json.JSONObject, index: Int): Int {
    val direct = cell.optInt("dbm", Int.MIN_VALUE)
    if (direct in -140..-40) return direct

    val average = cell.optInt("averageSignalStrength", Int.MIN_VALUE)
    if (average in -140..-40) return average

    val signal = cell.optInt("signal", Int.MIN_VALUE)
    if (signal in -140..-40) return signal

    return (-70 - index * 3).coerceAtLeast(-110)
}

internal fun LocationHooker.registeredCell(config: org.json.JSONObject?): org.json.JSONObject? =
    registeredCellJson(config?.optJSONArray("cell_json"))

internal fun LocationHooker.isTelephonyCallbackListener(
    classLoader: ClassLoader,
    callback: Any,
    listenerName: String
): Boolean {
    return LocationHooker.hasTypeByName(
        callback.javaClass,
        "android.telephony.TelephonyCallback\$$listenerName"
    )
}
