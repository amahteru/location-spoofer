@file:Suppress("SpellCheckingInspection", "unused")

package io.github.amahteru.locationspoofer.xposed.hooks.vendor

import io.github.amahteru.locationspoofer.vendor.RomFamily
import io.github.amahteru.locationspoofer.vendor.VendorProfile
import io.github.amahteru.locationspoofer.xposed.LocationHooker

interface SystemHookVendor {
    val usesFrameworkBleDelivery: Boolean get() = false

    val usesFrameworkLocationDelivery: Boolean get() = false

    val requiresVirtualLocationOptIn: Boolean get() = false

    val id: String

    val family: RomFamily

    val priority: Int

    fun matches(profile: VendorProfile): Boolean

    fun classCandidates(component: SystemComponent): List<String> = emptyList()

    fun installExtraHooks(hooker: LocationHooker, classLoader: ClassLoader) {}

    fun additionalExemptPackages(): Set<String> = emptySet()
}
