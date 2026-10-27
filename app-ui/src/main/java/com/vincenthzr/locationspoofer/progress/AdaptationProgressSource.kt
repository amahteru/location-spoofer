package com.vincenthzr.locationspoofer.progress

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class AdaptationProgressSource(context: Context) {

    enum class Origin { REMOTE, CACHE, BUNDLED }

    data class Document(val markdown: String, val origin: Origin)

    private val appContext = context.applicationContext
    private val english = context.resources.configuration.locales[0].language != "zh"
    private val fileName = if (english) FILE_NAME_EN else FILE_NAME
    private val marker = if (english) MARKER_EN else MARKER
    private val cacheFile = File(appContext.filesDir, fileName)

    fun local(): Document {
        runCatching { cacheFile.readText() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            return Document(it, Origin.CACHE)
        }
        val bundled = runCatching { appContext.assets.open(fileName).bufferedReader().use { it.readText() } }
        return Document(bundled.getOrDefault(""), Origin.BUNDLED)
    }

    suspend fun fetchRemote(): Document? = withContext(Dispatchers.IO) {
        for (url in REMOTE_URLS.map { it + fileName }) {
            val text = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body.string() else null
                }
            }.getOrNull()
            if (text != null && text.contains(marker)) {
                runCatching { cacheFile.writeText(text) }
                return@withContext Document(text, Origin.REMOTE)
            }
        }
        null
    }

    private companion object {
        const val FILE_NAME = "ADAPTATION_PROGRESS.md"
        const val FILE_NAME_EN = "ADAPTATION_PROGRESS_EN.md"
        const val MARKER = "# 适配进度"
        const val MARKER_EN = "# Adaptation progress"
        val REMOTE_URLS = listOf(
            "https://raw.githubusercontent.com/amahteru/location-spoofer/main/",
            "https://cdn.jsdelivr.net/gh/amahteru/location-spoofer@main/"
        )
        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
