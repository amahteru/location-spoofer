package io.github.amahteru.locationspoofer.xposed.utils

import android.content.SharedPreferences
import io.github.amahteru.locationspoofer.utils.FrameworkConfigChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class FrameworkConfigReceiver(
    private val getPreferences: () -> SharedPreferences,
    private val readFile: (String) -> String,
    private val onConfig: (FrameworkConfigSnapshot) -> Unit,
    private val onError: (Exception) -> Unit,
    private val readHead: () -> String? = { null }
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "LocationSpoofer-ConfigReceiver").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    private var preferences: SharedPreferences? = null
    private var lastSnapshot: String? = null
    private var lastHead: String? = null
    private var applied: FrameworkConfigSnapshot? = null
    private var lastErrorAt = 0L
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == FrameworkConfigChannel.SNAPSHOT_KEY) {
            if (!closed) runCatching { executor.execute { refresh() } }
        }
    }

    fun start() {
        refresh()
        executor.scheduleWithFixedDelay({ refresh() }, 1, 1, TimeUnit.SECONDS)
    }

    @Synchronized
    internal fun refresh() {
        if (closed) return
        try {
            val prefs = preferences ?: getPreferences().also {
                it.registerOnSharedPreferenceChangeListener(listener)
                preferences = it
            }
            val text = prefs.getString(FrameworkConfigChannel.SNAPSHOT_KEY, null)
            if (text != null && text != lastSnapshot) {
                deliver(FrameworkConfigSnapshot.decode(text, readFile), requireNewer = false)
                lastSnapshot = text
            }
        } catch (error: Exception) {
            preferences?.let { runCatching { it.unregisterOnSharedPreferenceChangeListener(listener) } }
            preferences = null
            report(error)
        }
        try {
            val head = readHead() ?: return
            if (head == lastHead) return
            deliver(FrameworkConfigSnapshot.decode(head, readFile).copy(source = HEAD_SOURCE), requireNewer = true)
            lastHead = head
        } catch (error: Exception) {
            report(error)
        }
    }

    private fun deliver(snapshot: FrameworkConfigSnapshot, requireNewer: Boolean) {
        if (closed) return
        val current = applied
        if (current != null && snapshot.id == current.id) return
        if (requireNewer && current != null && snapshot.publishedAt <= current.publishedAt) return
        onConfig(snapshot)
        applied = snapshot
    }

    private fun report(error: Exception) {
        val now = System.currentTimeMillis()
        if (now - lastErrorAt >= 60_000) {
            lastErrorAt = now
            runCatching { onError(error) }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        executor.shutdownNow()
        preferences?.let { runCatching { it.unregisterOnSharedPreferenceChangeListener(listener) } }
        preferences = null
    }

    companion object {
        const val HEAD_SOURCE = "libxposed:head-file"
    }
}
