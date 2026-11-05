package io.github.amahteru.locationspoofer.utils

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class WigleClient {
    private val client = OkHttpClient()

    suspend fun fetchWifiData(lat: Double, lng: Double, token: String): String =
        withContext(Dispatchers.IO) {
            val latrange1 = lat - 0.002
            val latrange2 = lat + 0.002
            val longrange1 = lng - 0.002
            val longrange2 = lng + 0.002

            val url =
                "https://api.wigle.net/api/v2/network/search?latrange1=$latrange1&latrange2=$latrange2&longrange1=$longrange1&longrange2=$longrange2"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic $token")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("WiGLE ${response.code}")
                val results = JSONObject(response.body.string()).optJSONArray("results")
                val wifiList = JSONArray()
                for (i in 0 until minOf(results?.length() ?: 0, MAX_RESULTS)) {
                    val item = results!!.getJSONObject(i)
                    val bssid = item.optString("netid")
                    val channel = item.optInt("channel")
                    if (bssid.isEmpty() || channel <= 0) continue
                    wifiList.put(
                        JSONObject()
                            .put("ssid", item.optString("ssid"))
                            .put("bssid", bssid)
                            .put("channel", channel)
                    )
                }
                wifiList.toString()
            }
        }

    private companion object {
        const val MAX_RESULTS = 10
    }
}
