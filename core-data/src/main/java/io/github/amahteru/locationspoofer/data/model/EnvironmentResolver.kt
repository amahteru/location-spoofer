package io.github.amahteru.locationspoofer.data.model

import io.github.amahteru.locationspoofer.data.db.CompleteCollectionRoute
import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.data.db.LocationWithBluetooth
import io.github.amahteru.locationspoofer.data.db.LocationWithCell
import io.github.amahteru.locationspoofer.data.db.LocationWithWifi
import io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng
import io.github.amahteru.locationspoofer.utils.GeoMath
import io.github.amahteru.locationspoofer.utils.MacVendorHelper
import org.json.JSONArray
import org.json.JSONObject

data class SignalCounts(val wifi: Int, val cell: Int, val bluetooth: Int)

object EnvironmentResolver {
    const val COVERAGE_RADIUS_M = 50.0

    fun recordsAt(
        locations: List<CompleteLocation>,
        routes: List<CompleteCollectionRoute>,
        lat: Double,
        lng: Double,
        preferredRouteId: Long? = null
    ): List<CompleteLocation> {
        val position = LatLng(lat, lng)
        val route = CollectionRouteMatcher.routeAt(routes, position, preferredRouteId)
        if (route != null) return CollectionRouteMatcher.samplesAt(route, position)
        return locations
            .filter { it.location.collectionRouteId == null && GeoMath.distance(position, LatLng(it.location.lat, it.location.lng)) <= COVERAGE_RADIUS_M }
            .sortedBy { GeoMath.distance(position, LatLng(it.location.lat, it.location.lng)) }
    }

    fun hasWifi(records: List<CompleteLocation>) = records.any { it.wifis.isNotEmpty() || it.connectedWifi != null }

    fun hasCell(records: List<CompleteLocation>) = records.any { it.cells.isNotEmpty() }

    fun hasBluetooth(records: List<CompleteLocation>) = records.any { it.bluetooths.isNotEmpty() }

    fun signalCounts(records: List<CompleteLocation>) = SignalCounts(
        wifi = records.flatMap { r -> r.wifis.map { it.device.bssid } + listOfNotNull(r.connectedWifi?.bssid) }.toSet().size,
        cell = records.flatMap { r -> r.cells.map { it.device.cellKey } }.toSet().size,
        bluetooth = records.flatMap { r -> r.bluetooths.map { it.device.address } }.toSet().size
    )

    fun toJson(
        records: List<CompleteLocation>,
        targetLat: Double,
        targetLng: Double,
        pinnedId: Long? = null
    ): Triple<String, String, String> {
        if (records.isEmpty()) return Triple("{}", "[]", "[]")

        val weights = records.mapIndexed { i, it ->
            if (i == 0 && (it.location.id == pinnedId || it.location.selectedWifiBssid != null)) {
                1000.0
            } else {
                val dist = GeoMath.distance(LatLng(targetLat, targetLng), LatLng(it.location.lat, it.location.lng))
                val safeDist = kotlin.math.max(dist, 1.0)
                1.0 / (safeDist * safeDist)
            }
        }

        val closestRecord = records.first()
        val explicitWifiBssid = closestRecord.location?.selectedWifiBssid
        val explicitWifi =
            if (explicitWifiBssid != null) {
                closestRecord.wifis.find { it.device.bssid.equals(explicitWifiBssid, ignoreCase = true) }
            } else null

        val closestConnectedWifi = closestRecord.connectedWifi
        val connectedObj = if (explicitWifi != null) {
            JSONObject().apply {
                put("ssid", explicitWifi.device.ssid)
                put("bssid", explicitWifi.device.bssid)
                put("vendor", explicitWifi.device.vendor)
                put("macAddress", explicitWifi.device.bssid)
                put("frequency", explicitWifi.device.frequency)
                put(
                    "channel",
                    MacVendorHelper.frequencyToChannel(
                        explicitWifi.device.frequency
                    )
                )
                put("linkSpeed", 65)
                put("level", explicitWifi.locationWifi.level)
                put("capabilities", explicitWifi.device.capabilities)
                put("networkId", 1)
                put("wifiStandard", 6)
            }
        } else if (closestConnectedWifi != null && (explicitWifiBssid == null || explicitWifiBssid.equals(closestConnectedWifi.bssid, ignoreCase = true))) {
            val cw = closestConnectedWifi
            JSONObject().apply {
                put("ssid", cw.ssid)
                put("bssid", cw.bssid)
                put("vendor", cw.vendor)
                put("macAddress", cw.macAddress)
                put("frequency", cw.frequency)
                put(
                    "channel",
                    MacVendorHelper.frequencyToChannel(cw.frequency)
                )
                put("linkSpeed", cw.linkSpeed)
                put("level", cw.level)
                put("capabilities", cw.capabilities)
                put("networkId", cw.networkId)
                put("wifiStandard", cw.wifiStandard)
            }
        } else {
            null
        }

        val wifiMap = mutableMapOf<String, LocationWithWifi>()
        val wifiLevels = mutableMapOf<String, Double>()
        val wifiWeights = mutableMapOf<String, Double>()

        records.forEachIndexed { i, rec ->
            rec.wifis.forEach { rw ->
                val bssid = rw.device.bssid
                if (!wifiMap.containsKey(bssid)) wifiMap[bssid] = rw
                wifiLevels[bssid] = (wifiLevels[bssid] ?: 0.0) + rw.locationWifi.level * weights[i]
                wifiWeights[bssid] = (wifiWeights[bssid] ?: 0.0) + weights[i]
            }
        }

        val nearbyArr = JSONArray()
        wifiMap.forEach { (bssid, rw) ->
            val w = wifiWeights[bssid]!!
            val interpolatedLevel = (wifiLevels[bssid]!! / w).toInt()
            val obj = JSONObject().apply {
                put("bssid", bssid)
                put("ssid", rw.device.ssid)
                put("vendor", rw.device.vendor)
                put("frequency", rw.device.frequency)
                put(
                    "channel",
                    MacVendorHelper.frequencyToChannel(rw.device.frequency)
                )
                put("capabilities", rw.device.capabilities)
                put("level", interpolatedLevel)
            }
            nearbyArr.put(obj)
        }

        val hasConnected = connectedObj != null
        val wifiResultObj = JSONObject().apply {
            put("isConnected", hasConnected)
            put("connectedWifi", connectedObj ?: JSONObject.NULL)
            put("nearbyWifi", nearbyArr)
        }

        val cellMap = mutableMapOf<String, LocationWithCell>()
        val cellDbms = mutableMapOf<String, Double>()
        val cellWeights = mutableMapOf<String, Double>()

        records.forEachIndexed { i, rec ->
            rec.cells.forEach { rc ->
                val cellKey = rc.device.cellKey
                if (!cellMap.containsKey(cellKey)) cellMap[cellKey] = rc
                cellDbms[cellKey] = (cellDbms[cellKey] ?: 0.0) + rc.locationCell.dbm * weights[i]
                cellWeights[cellKey] = (cellWeights[cellKey] ?: 0.0) + weights[i]
            }
        }

        val explicitCellKey = closestRecord.location?.selectedCellKey
        val cellArr = JSONArray()
        val cellList = mutableListOf<JSONObject>()
        cellMap.forEach { (cellKey, rc) ->
            val w = cellWeights[cellKey]!!
            val interpolatedDbm = (cellDbms[cellKey]!! / w).toInt()
            val obj = JSONObject()
            obj.put("type", rc.device.type)
            obj.put("mcc", rc.device.mcc)
            obj.put("mnc", rc.device.mnc)
            obj.put("tac", rc.device.tac)
            obj.put("ci", rc.device.ci)
            obj.put("pci", rc.device.pci)
            obj.put("lac", rc.device.lac)
            obj.put("cid", rc.device.cid)
            obj.put("psc", rc.device.psc)
            obj.put("nci", rc.device.nci)
            obj.put("networkId", rc.device.networkId)
            obj.put("systemId", rc.device.systemId)
            obj.put("basestationId", rc.device.basestationId)
            obj.put("dbm", interpolatedDbm)
            val isReg =
                if (explicitCellKey != null) cellKey.equals(explicitCellKey, ignoreCase = true) else rc.locationCell.isRegistered
            obj.put("isRegistered", isReg)
            if (isReg) {
                cellList.add(0, obj)
            } else {
                cellList.add(obj)
            }
        }
        cellList.forEach { cellArr.put(it) }

        val btMap =
            mutableMapOf<String, LocationWithBluetooth>()
        val btRssis = mutableMapOf<String, Double>()
        val btWeights = mutableMapOf<String, Double>()

        records.forEachIndexed { i, rec ->
            rec.bluetooths.forEach { rb ->
                val address = rb.device.address
                if (!btMap.containsKey(address)) btMap[address] = rb
                btRssis[address] =
                    (btRssis[address] ?: 0.0) + rb.locationBluetooth.rssi * weights[i]
                btWeights[address] = (btWeights[address] ?: 0.0) + weights[i]
            }
        }

        val explicitBtAddress = closestRecord.location?.selectedBluetoothAddress
        val btArr = JSONArray()
        val btList = mutableListOf<JSONObject>()
        btMap.forEach { (address, rb) ->
            val w = btWeights[address]!!
            val interpolatedRssi = (btRssis[address]!! / w).toInt()
            val obj = JSONObject()
            obj.put("address", address)
            obj.put("name", rb.device.name)
            obj.put("scanRecordHex", rb.device.scanRecordHex)
            obj.put("rssi", interpolatedRssi)
            val isSelectedBt = explicitBtAddress != null && explicitBtAddress.equals(address, ignoreCase = true)
            if (isSelectedBt) {
                obj.put("isConnected", true)
                btList.add(0, obj)
            } else {
                btList.add(obj)
            }
        }
        btList.forEach { btArr.put(it) }

        return Triple(wifiResultObj.toString(), cellArr.toString(), btArr.toString())
    }
}
