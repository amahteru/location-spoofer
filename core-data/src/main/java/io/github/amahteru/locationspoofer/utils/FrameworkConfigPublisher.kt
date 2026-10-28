package io.github.amahteru.locationspoofer.utils

import org.json.JSONObject
import java.util.UUID

internal interface FrameworkConfigStore {
    fun commit(snapshot: String): Boolean
    fun writeFile(name: String, payload: ByteArray)
    fun writeHead(snapshot: String)
    fun listFiles(): List<String>
    fun deleteFile(name: String)
}

internal class FrameworkConfigPublisher {
    private var cleanupNeeded = true

    fun publish(payload: String, store: FrameworkConfigStore): Long {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val publishedAt = System.currentTimeMillis()
        val file = if (bytes.size > FrameworkConfigChannel.INLINE_LIMIT_BYTES) {
            "${FrameworkConfigChannel.FILE_PREFIX}${UUID.randomUUID()}.json"
        } else null
        var commitAttempted = false
        val snapshot: String
        try {
            if (file != null) store.writeFile(file, bytes)
            snapshot = JSONObject().apply {
                put("version", FrameworkConfigChannel.VERSION)
                put("id", UUID.randomUUID().toString())
                put("published_at", publishedAt)
                if (file == null) put("payload", payload) else put("file", file)
            }.toString()
            commitAttempted = true
            check(store.commit(snapshot)) { "Framework rejected config publication" }
        } catch (error: Exception) {
            if (file != null && !commitAttempted) runCatching { store.deleteFile(file) }
            cleanupNeeded = true
            throw error
        }
        runCatching { store.writeHead(snapshot) }
        if (cleanupNeeded || file != null) runCatching {
            store.listFiles().filter { FrameworkConfigChannel.isConfigFile(it) && it != file }
                .forEach { store.deleteFile(it) }
            cleanupNeeded = file != null
        }
        return publishedAt
    }

    fun onReconnect() { cleanupNeeded = true }
}
