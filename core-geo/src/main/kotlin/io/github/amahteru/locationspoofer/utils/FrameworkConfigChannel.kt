package io.github.amahteru.locationspoofer.utils

object FrameworkConfigChannel {
    const val GROUP = "locationspoofer_config"
    const val SNAPSHOT_KEY = "snapshot"
    const val VERSION = 1
    const val INLINE_LIMIT_BYTES = 128 * 1024
    const val FILE_PREFIX = "locationspoofer-config-"
    const val HEAD_FILE = "locationspoofer-head.json"

    fun isConfigFile(name: String): Boolean =
        name.startsWith(FILE_PREFIX) && name.endsWith(".json") &&
            '/' !in name && '\\' !in name && ".." !in name
}
