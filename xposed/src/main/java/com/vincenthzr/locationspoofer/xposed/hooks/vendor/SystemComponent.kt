@file:Suppress("SpellCheckingInspection", "unused")

package com.vincenthzr.locationspoofer.xposed.hooks.vendor

enum class SystemProcess {
    SYSTEM_SERVER,
    PHONE,
    BLUETOOTH,
}

enum class SystemComponent(val process: SystemProcess) {
    LOCATION_MANAGER_SERVICE(SystemProcess.SYSTEM_SERVER),

    LOCATION_PROVIDER_MANAGER(SystemProcess.SYSTEM_SERVER),

    WIFI_SERVICE(SystemProcess.SYSTEM_SERVER),

    WIFI_SCANNER_SERVICE(SystemProcess.SYSTEM_SERVER),

    CONNECTIVITY_SERVICE(SystemProcess.SYSTEM_SERVER),

    TELEPHONY_REGISTRY(SystemProcess.SYSTEM_SERVER),

    APPOPS_SERVICE(SystemProcess.SYSTEM_SERVER),

    TELEPHONY_PHONE_MANAGER(SystemProcess.PHONE),

    BLUETOOTH_SCAN_SERVICE(SystemProcess.BLUETOOTH),
}
