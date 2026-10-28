package io.github.amahteru.locationspoofer.data.repository

import io.github.amahteru.locationspoofer.utils.WigleClient

class WifiRepository(private val wigleClient: WigleClient) {

    suspend fun fetchWifiData(lat: Double, lng: Double, token: String): String =
        wigleClient.fetchWifiData(lat, lng, token)
}
