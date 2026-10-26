package com.vincenthzr.locationspoofer.utils

/** Shared wire contract for the module app and injected processes. */
object FrameworkConfigChannel {
    const val GROUP = "locationspoofer_config"
    const val SNAPSHOT_KEY = "snapshot"
    const val VERSION = 1
    // Leave ample room for Binder's other in-flight transactions. Larger data uses an FD.
    const val INLINE_LIMIT_BYTES = 128 * 1024
    const val FILE_PREFIX = "locationspoofer-config-"
    // Copy of the latest snapshot pointer. Readers fetch files from the framework on every read, so this
    // still works in processes whose remote preferences silently stop updating (seen in system_server).
    // Deliberately outside FILE_PREFIX so publication cleanup never deletes it.
    const val HEAD_FILE = "locationspoofer-head.json"

    fun isConfigFile(name: String): Boolean =
        name.startsWith(FILE_PREFIX) && name.endsWith(".json") &&
            '/' !in name && '\\' !in name && ".." !in name
}
