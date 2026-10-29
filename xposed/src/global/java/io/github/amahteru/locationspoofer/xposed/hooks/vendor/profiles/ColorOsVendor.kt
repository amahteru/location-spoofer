@file:Suppress("SpellCheckingInspection", "unused")

package io.github.amahteru.locationspoofer.xposed.hooks.vendor.profiles

import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemComponent
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemHookVendor
import io.github.amahteru.locationspoofer.vendor.RomFamily
import io.github.amahteru.locationspoofer.vendor.VendorProfile
import io.github.amahteru.locationspoofer.vendor.VendorScheme

object ColorOsVendor : SystemHookVendor {
    override val id = VendorScheme.COLOROS.id
    override val family = RomFamily.COLOROS
    override val priority = 90

    override fun matches(profile: VendorProfile) = profile.family == family

    override fun classCandidates(component: SystemComponent): List<String> = emptyList()
}
