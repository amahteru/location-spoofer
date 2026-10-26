package com.vincenthzr.locationspoofer.xposed.utils

import android.content.SharedPreferences
import com.vincenthzr.locationspoofer.utils.FrameworkConfigChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** All framework access and JSON decoding happen outside the hooked call path. */
internal class FrameworkConfigReceiver(
    private val getPreferences: () -> SharedPreferences,
    private val readFile: (String) -> String,
    private val onConfig: (FrameworkConfigSnapshot) -> Unit,
    private val onError: (Exception) -> Unit,
    /** Reads [FrameworkConfigChannel.HEAD_FILE]; null when it does not exist yet. */
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
    // Keep a strong reference: SharedPreferences implementations may store weak listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == FrameworkConfigChannel.SNAPSHOT_KEY) {
            if (!closed) runCatching { executor.execute { refresh() } }
        }
    }

    fun start() {
        // Module lifecycle initialization precedes hook installation. Hook reads remain memory-only.
        refresh()
        // Retry early-boot failures and reconcile the latest snapshot if a notification was missed.
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
            // Leave the last valid memory snapshot intact; a failed blob read is retried next tick.
            preferences?.let { runCatching { it.unregisterOnSharedPreferenceChangeListener(listener) } }
            preferences = null
            report(error)
        }
        // Remote preferences can stop updating without any callback or error (seen in system_server,
        // stuck on the boot-time snapshot), so also check the head copy, which is read fresh each time.
        try {
            val head = readHead() ?: return
            if (head == lastHead) return
            // A torn read of a head being rewritten fails to decode and is simply retried next tick.
            deliver(FrameworkConfigSnapshot.decode(head, readFile).copy(source = HEAD_SOURCE), requireNewer = true)
            lastHead = head
        } catch (error: Exception) {
            report(error)
        }
    }

    /**
     * Both channels carry the same snapshots, so one already in memory is skipped. A changed preferences
     * value is always a new publication and is applied as is: comparing timestamps there would drop a
     * stop request published after the device clock moved backwards. Only the head copy, the fallback,
     * must be strictly newer, so it can never roll back what preferences delivered.
     */
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
        /** Shown on the hook status page when the head copy delivered the config. */
        const val HEAD_SOURCE = "libxposed:head-file"
    }
}
