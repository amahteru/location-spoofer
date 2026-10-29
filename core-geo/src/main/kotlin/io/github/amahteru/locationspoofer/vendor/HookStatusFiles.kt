package io.github.amahteru.locationspoofer.vendor

object HookStatusFiles {

    val PATHS: Map<String, List<String>> = linkedMapOf(
        "SYSTEM_SERVER" to listOf("/data/system/locationspoofer_status.json"),
        "PHONE" to listOf(
            "/data/user_de/0/com.android.phone/files/locationspoofer_status.json",
            "/data/user_de/0/com.android.phone/locationspoofer_status.json"
        ),
        "BLUETOOTH" to listOf(
            "/data/user_de/0/com.android.bluetooth/files/locationspoofer_status.json",
            "/data/user_de/0/com.android.bluetooth/locationspoofer_status.json"
        ),
    )

    fun paths(process: String): List<String> = PATHS[process].orEmpty()
}
