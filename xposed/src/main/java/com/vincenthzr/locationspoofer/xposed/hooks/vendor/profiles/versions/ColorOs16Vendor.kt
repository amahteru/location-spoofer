package com.vincenthzr.locationspoofer.xposed.hooks.vendor.profiles.versions

import com.vincenthzr.locationspoofer.vendor.VendorProfile
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemComponent
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemVersionVendor
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.profiles.ColorOsVendor

object ColorOs16Vendor : SystemVersionVendor(ColorOsVendor) {
    override val id = "coloros16"
    override val usesFrameworkBleDelivery = true
    override val usesFrameworkLocationDelivery = true
    override val requiresVirtualLocationOptIn = true

    override fun matchesVersion(profile: VendorProfile): Boolean =
        com.vincenthzr.locationspoofer.vendor.RomRules.isColorOs16(profile)

    override fun classCandidates(component: SystemComponent): List<String> = when (component) {
        SystemComponent.BLUETOOTH_SCAN_SERVICE -> listOf(
            "com.android.bluetooth.le_scan.TransitionalScanHelper"
        )
        SystemComponent.CONNECTIVITY_SERVICE -> listOf(
            "android.net.connectivity.com.android.server.ConnectivityService"
        )
        else -> super.classCandidates(component)
    }

    override fun installExtraHooks(hooker: LocationHooker, classLoader: ClassLoader) {
        super.installExtraHooks(hooker, classLoader)
        if (hooker.isBluetoothProcessInstance && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val pendingIntent = ColorOs16BleDelivery(hooker, classLoader)
            hooker.vendorExtraHooks.add(pendingIntent)
            pendingIntent.install()
        }
        if (hooker.isSystemServerProcess) {
            val extra = ColorOs16LocationAvailability(hooker, classLoader)
            hooker.vendorExtraHooks.add(extra)
            extra.install()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val delivery = ColorOs16LocationDelivery(hooker, classLoader)
                hooker.vendorExtraHooks.add(delivery)
                delivery.install()
            }
        }
    }
}
