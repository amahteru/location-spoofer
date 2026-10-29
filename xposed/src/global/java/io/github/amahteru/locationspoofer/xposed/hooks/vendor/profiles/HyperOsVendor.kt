@file:Suppress("SpellCheckingInspection", "unused")

package io.github.amahteru.locationspoofer.xposed.hooks.vendor.profiles

import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemComponent
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemHookVendor
import io.github.amahteru.locationspoofer.vendor.RomFamily
import io.github.amahteru.locationspoofer.vendor.VendorProfile
import io.github.amahteru.locationspoofer.vendor.VendorScheme

object HyperOsVendor : SystemHookVendor {
    override val id = VendorScheme.HYPEROS.id
    override val family = RomFamily.HYPEROS_MIUI
    override val priority = 100

    override fun matches(profile: VendorProfile) = profile.family == family

    override fun classCandidates(component: SystemComponent): List<String> = emptyList()

    override fun additionalExemptPackages(): Set<String> = setOf(
        "com.xiaomi.metoknlp",
        "com.xiaomi.location.fused",
        "com.xiaomi.aicr",
        "com.xiaomi.hypercomm",
        "com.miui.powerinsight",
        "com.miui.powerkeeper",
    )

    override fun installExtraHooks(hooker: LocationHooker, classLoader: ClassLoader) {
    }
}
