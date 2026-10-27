@file:Suppress("SpellCheckingInspection", "unused")

package com.vincenthzr.locationspoofer.xposed.hooks.vendor.profiles

import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemComponent
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.SystemHookVendor
import com.vincenthzr.locationspoofer.vendor.RomFamily
import com.vincenthzr.locationspoofer.vendor.VendorProfile
import com.vincenthzr.locationspoofer.vendor.VendorScheme

object AospVendor : SystemHookVendor {
    override val id = VendorScheme.AOSP.id
    override val family = RomFamily.AOSP
    override val priority = Int.MIN_VALUE
    override fun matches(profile: VendorProfile) = true

    override fun classCandidates(component: SystemComponent): List<String> = when (component) {
        SystemComponent.LOCATION_MANAGER_SERVICE -> listOf(
            "com.android.server.location.LocationManagerService",
            "com.android.server.LocationManagerService",
        )
        SystemComponent.LOCATION_PROVIDER_MANAGER -> listOf(
            "com.android.server.location.provider.LocationProviderManager",
        )
        SystemComponent.WIFI_SERVICE -> listOf(
            "com.android.server.wifi.WifiServiceImpl",
            "com.android.server.WifiService",
        )
        SystemComponent.WIFI_SCANNER_SERVICE -> listOf(
            "com.android.server.wifi.scanner.WifiScanningServiceImpl",
        )
        SystemComponent.CONNECTIVITY_SERVICE -> listOf(
            "com.android.server.ConnectivityService",
            "com.android.server.connectivity.ConnectivityService",
        )
        SystemComponent.TELEPHONY_PHONE_MANAGER -> listOf(
            "com.android.internal.telephony.PhoneInterfaceManager",
            "com.android.server.telephony.PhoneInterfaceManager",
            "com.android.phone.PhoneInterfaceManager",
        )
        SystemComponent.TELEPHONY_REGISTRY -> listOf(
            "com.android.server.TelephonyRegistry",
        )
        SystemComponent.APPOPS_SERVICE -> listOf(
            "com.android.server.appop.AppOpsService",
            "com.android.server.AppOpsService",
        )
        SystemComponent.BLUETOOTH_SCAN_SERVICE -> listOf(
            "com.android.bluetooth.le_scan.ScanBinder",
            "com.android.bluetooth.le_scan.TransitionalScanHelper",
            "com.android.bluetooth.gatt.GattService",
            "com.android.bluetooth.btservice.AdapterService",
        )
    }
}
