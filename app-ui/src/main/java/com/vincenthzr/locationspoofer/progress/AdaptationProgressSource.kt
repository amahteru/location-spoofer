package com.vincenthzr.locationspoofer.progress

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 适配进度文档的来源：优先联网取仓库 main 分支的最新版（改了文档不用发版用户就能看到），
 * 取不到时依次退回上次成功获取的缓存、安装包内置的一份（构建时从仓库根目录复制进 assets）。
 * 中文界面读 ADAPTATION_PROGRESS.md，其余语言读英文版 ADAPTATION_PROGRESS_EN.md，缓存按文件分开存。
 */
class AdaptationProgressSource(context: Context) {

    enum class Origin { REMOTE, CACHE, BUNDLED }

    data class Document(val markdown: String, val origin: Origin)

    private val appContext = context.applicationContext
    // 语言取自传入的 Context（Activity 已套用应用内语言），applicationContext 可能仍是系统语言
    private val english = context.resources.configuration.locales[0].language != "zh"
    private val fileName = if (english) FILE_NAME_EN else FILE_NAME
    private val marker = if (english) MARKER_EN else MARKER
    private val cacheFile = File(appContext.filesDir, fileName)

    /** 不联网即可读到的版本：缓存优先，没有缓存用内置 */
    fun local(): Document {
        runCatching { cacheFile.readText() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            return Document(it, Origin.CACHE)
        }
        val bundled = runCatching { appContext.assets.open(fileName).bufferedReader().use { it.readText() } }
        return Document(bundled.getOrDefault(""), Origin.BUNDLED)
    }

    /** 依次尝试 GitHub 与 jsDelivr 镜像（国内网络常连不上 raw.githubusercontent.com），成功后写入缓存 */
    suspend fun fetchRemote(): Document? = withContext(Dispatchers.IO) {
        for (url in REMOTE_URLS.map { it + fileName }) {
            val text = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull()
            // 只接受看起来确实是这份文档的内容，避免把代理 / 运营商的拦截页存进缓存
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
