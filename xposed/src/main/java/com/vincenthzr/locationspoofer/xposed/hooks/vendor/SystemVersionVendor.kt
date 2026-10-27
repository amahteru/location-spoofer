@file:Suppress("SpellCheckingInspection", "unused", "MemberVisibilityCanBePrivate")

package com.vincenthzr.locationspoofer.xposed.hooks.vendor

import com.vincenthzr.locationspoofer.vendor.RomFamily
import com.vincenthzr.locationspoofer.vendor.VendorProfile
import com.vincenthzr.locationspoofer.xposed.LocationHooker

abstract class SystemVersionVendor(
    protected val parent: SystemHookVendor,
) : SystemHookVendor {
    protected abstract fun matchesVersion(profile: VendorProfile): Boolean

    override val usesFrameworkBleDelivery: Boolean get() = parent.usesFrameworkBleDelivery

    override val usesFrameworkLocationDelivery: Boolean get() = parent.usesFrameworkLocationDelivery

    override val requiresVirtualLocationOptIn: Boolean get() = parent.requiresVirtualLocationOptIn

    override val family: RomFamily get() = parent.family

    override val priority: Int = parent.priority + 10

    final override fun matches(profile: VendorProfile): Boolean {
        if (!parent.matches(profile)) return false
        return try {
            matchesVersion(profile)
        } catch (_: Throwable) {
            false
        }
    }

    override fun classCandidates(component: SystemComponent): List<String> = parent.classCandidates(component)

    override fun additionalExemptPackages(): Set<String> = parent.additionalExemptPackages()

    override fun installExtraHooks(hooker: LocationHooker, classLoader: ClassLoader) {
        parent.installExtraHooks(hooker, classLoader)
    }
}
