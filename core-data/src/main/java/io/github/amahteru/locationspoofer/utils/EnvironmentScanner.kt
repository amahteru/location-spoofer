package io.github.amahteru.locationspoofer.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@android.annotation.SuppressLint("MissingPermission")
class EnvironmentScanner(private val context: Context) {

    @SuppressLint("MissingPermission")
    private suspend fun connectedWifiInfo(connectivityManager: ConnectivityManager): WifiInfo? {
        var callback: ConnectivityManager.NetworkCallback? = null
        return try {
            withTimeoutOrNull(WIFI_INFO_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val networkCallback =
                        object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                                val info = caps.transportInfo as? WifiInfo ?: return
                                if (continuation.isActive) continuation.resume(info)
                            }
                        }
                    callback = networkCallback
                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build()
                    connectivityManager.registerNetworkCallback(request, networkCallback)
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun scanWifi(): String = withContext(Dispatchers.IO) {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val resultObj = JSONObject()
        try {
            val connectionInfo = connectedWifiInfo(connectivityManager)
            val isWifiConnected = connectionInfo != null

            resultObj.put("isConnected", isWifiConnected)

            val connectedBssid = connectionInfo?.bssid
            try {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
            } catch (e: Throwable) {
            }
            val results = wifiManager.scanResults ?: emptyList()

            if (connectionInfo != null && connectedBssid != null && connectedBssid != "02:00:00:00:00:00") {
                val connObj = JSONObject()
                connObj.put("bssid", connectedBssid)
                val rawSsid = connectionInfo.ssid
                var cleanSsid =
                    if (rawSsid != null && rawSsid.startsWith("\"") && rawSsid.endsWith("\"")) {
                        rawSsid.substring(1, rawSsid.length - 1)
                    } else {
                        rawSsid ?: ""
                    }

                val match = results.find { it.BSSID == connectedBssid }
                if (cleanSsid == "<unknown ssid>" || cleanSsid.isEmpty()) {
                    val matchSsid = match?.ssidText()
                    if (!matchSsid.isNullOrEmpty()) {
                        cleanSsid = matchSsid
                    }
                }

                connObj.put("ssid", if (cleanSsid == "<unknown ssid>") "" else cleanSsid)
                connObj.put("vendor", MacVendorHelper.getVendor(connectedBssid))
                connObj.put("level", connectionInfo.rssi)
                connObj.put("frequency", connectionInfo.frequency)
                connObj.put("channel", MacVendorHelper.frequencyToChannel(connectionInfo.frequency))
                connObj.put("capabilities", match?.capabilities ?: "[WPA2-PSK-CCMP][ESS]")
                connObj.put("macAddress", "02:00:00:00:00:00")
                try {
                    connObj.put("linkSpeed", connectionInfo.linkSpeed)
                } catch (e: Throwable) {
                }
                try {
                    connObj.put("networkId", connectionInfo.networkId)
                } catch (e: Throwable) {
                }
                try {
                    connObj.put("wifiStandard", connectionInfo.wifiStandard)
                } catch (e: Throwable) {
                }
                resultObj.put("connectedWifi", connObj)
            } else {
                resultObj.put("connectedWifi", JSONObject.NULL)
            }

            val nearbyArray = JSONArray()
            val seenBssids = mutableSetOf<String>()
            if (isWifiConnected && connectedBssid != null) {
                seenBssids.add(connectedBssid)
            }
            results.forEach { scanResult ->
                val bssid = scanResult.BSSID
                if (bssid != null && !seenBssids.contains(bssid)) {
                    seenBssids.add(bssid)
                    val obj = JSONObject()
                    obj.put("bssid", bssid)
                    obj.put("ssid", scanResult.ssidText())
                    obj.put("vendor", MacVendorHelper.getVendor(bssid))
                    obj.put("level", scanResult.level)
                    obj.put("capabilities", scanResult.capabilities ?: "")
                    obj.put("frequency", scanResult.frequency)
                    obj.put("channel", MacVendorHelper.frequencyToChannel(scanResult.frequency))
                    nearbyArray.put(obj)
                }
            }
            resultObj.put("nearbyWifi", nearbyArray)
        } catch (e: Exception) {
            e.printStackTrace()
            resultObj.put("isConnected", false)
            resultObj.put("connectedWifi", JSONObject.NULL)
            resultObj.put("nearbyWifi", JSONArray())
        }
        resultObj.toString()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun scanCell(): String = withContext(Dispatchers.IO) {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val jsonArray = JSONArray()
        try {
            val allCellInfo =
                try {
                    withTimeoutOrNull(3000L) {
                        suspendCancellableCoroutine { continuation ->
                            telephonyManager.requestCellInfoUpdate(
                                context.mainExecutor,
                                object : TelephonyManager.CellInfoCallback() {
                                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                        if (continuation.isActive) continuation.resume(cellInfo)
                                    }

                                    override fun onError(errorCode: Int, detail: Throwable?) {
                                        if (continuation.isActive) continuation.resume(
                                            telephonyManager.allCellInfo ?: emptyList()
                                        )
                                    }
                                }
                            )
                        }
                    } ?: (telephonyManager.allCellInfo ?: emptyList())
                } catch (e: Exception) {
                    telephonyManager.allCellInfo ?: emptyList()
                }

            if (allCellInfo.isEmpty()) {
                val cellLoc = try {
                    telephonyManager.cellLocation
                } catch (e: Exception) {
                    null
                }
                if (cellLoc is android.telephony.gsm.GsmCellLocation) {
                    val obj = JSONObject()
                    obj.put("isRegistered", true)
                    obj.put("type", "GSM")
                    obj.put("lac", cellLoc.lac)
                    obj.put("cid", cellLoc.cid)
                    obj.put("psc", cellLoc.psc)
                    obj.put("dbm", -85)
                    obj.put("mcc", 460)
                    obj.put("mnc", 0)
                    jsonArray.put(obj)
                } else if (cellLoc is android.telephony.cdma.CdmaCellLocation) {
                    val obj = JSONObject()
                    obj.put("isRegistered", true)
                    obj.put("type", "CDMA")
                    obj.put("networkId", cellLoc.networkId)
                    obj.put("systemId", cellLoc.systemId)
                    obj.put("basestationId", cellLoc.baseStationId)
                    obj.put("dbm", -85)
                    jsonArray.put(obj)
                }
            }

            allCellInfo.forEach { cellInfo ->
                val obj = JSONObject()
                obj.put("isRegistered", cellInfo.isRegistered)

                when (cellInfo) {
                    is CellInfoLte -> {
                        obj.put("type", "LTE")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("tac", id.tac)
                        obj.put("ci", id.ci)
                        obj.put("pci", id.pci)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }

                    is CellInfoGsm -> {
                        obj.put("type", "GSM")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("lac", id.lac)
                        obj.put("cid", id.cid)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }

                    is CellInfoWcdma -> {
                        obj.put("type", "WCDMA")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("lac", id.lac)
                        obj.put("cid", id.cid)
                        obj.put("psc", id.psc)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }

                    is CellInfoNr -> {
                        obj.put("type", "NR")
                        val id = cellInfo.cellIdentity as? android.telephony.CellIdentityNr
                        obj.put("mcc", id?.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id?.mncString?.toIntOrNull() ?: 0)
                        obj.put("tac", id?.tac ?: Int.MAX_VALUE)
                        obj.put("nci", id?.nci ?: Long.MAX_VALUE)
                        obj.put("pci", id?.pci ?: Int.MAX_VALUE)
                        val signal =
                            cellInfo.cellSignalStrength as? android.telephony.CellSignalStrengthNr
                        obj.put("dbm", signal?.dbm ?: 0)
                    }

                    is CellInfoCdma -> {
                        obj.put("type", "CDMA")
                        val id = cellInfo.cellIdentity
                        obj.put("networkId", id.networkId)
                        obj.put("systemId", id.systemId)
                        obj.put("basestationId", id.basestationId)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }
                }
                jsonArray.put(obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        jsonArray.toString()
    }

    @SuppressLint("MissingPermission")
    suspend fun scanBluetooth(): String = withContext(Dispatchers.IO) {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner

        val jsonArray = JSONArray()
        if (scanner == null || !adapter.isEnabled) {
            return@withContext jsonArray.toString()
        }

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val resultsList = mutableListOf<ScanResult>()
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        if (result != null) {
                            resultsList.add(result)
                        }
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                        if (results != null) {
                            resultsList.addAll(results)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                    }
                }

                scanner.startScan(callback)

                kotlinx.coroutines.CoroutineScope(cont.context).launch {
                    delay(2000)
                    try {
                        scanner.stopScan(callback)
                    } catch (e: Exception) {
                    }

                    val deduped = resultsList.distinctBy { it.device.address }
                    deduped.forEach { res ->
                        val obj = JSONObject()
                        obj.put("address", res.device.address)
                        obj.put("name", res.device.name ?: "")
                        obj.put("rssi", res.rssi)

                        val recordBytes = res.scanRecord?.bytes
                        if (recordBytes != null) {
                            val hexString = recordBytes.joinToString("") { "%02X".format(it) }
                            obj.put("scanRecordHex", hexString)
                        }
                        jsonArray.put(obj)
                    }
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }

                cont.invokeOnCancellation {
                    try {
                        scanner.stopScan(callback)
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        jsonArray.toString()
    }

    private fun android.net.wifi.ScanResult.ssidText(): String =
        wifiSsid?.toString()?.removeSurrounding("\"").orEmpty()

    companion object {
        private const val WIFI_INFO_TIMEOUT_MS = 1_500L
    }
}
