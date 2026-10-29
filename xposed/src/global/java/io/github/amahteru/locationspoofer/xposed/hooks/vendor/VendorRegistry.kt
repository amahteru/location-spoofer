@file:Suppress("SpellCheckingInspection", "unused")

package io.github.amahteru.locationspoofer.xposed.hooks.vendor

import io.github.amahteru.locationspoofer.vendor.VendorProfile
import io.github.amahteru.locationspoofer.vendor.VendorScheme
import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.profiles.AospVendor
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.profiles.ColorOsVendor
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.profiles.HyperOsVendor
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.profiles.OneUiVendor
import io.github.amahteru.locationspoofer.xposed.utils.XposedBridge

object VendorRegistry {
    internal val ALL: List<SystemHookVendor> = listOf(
        io.github.amahteru.locationspoofer.xposed.hooks.vendor.profiles.versions.ColorOs16Vendor,
        HyperOsVendor,
        ColorOsVendor,
        OneUiVendor,
        AospVendor,
    )

    @Volatile
    private var manualOverrideId: String? = null

    fun applyManualOverride(id: String?) {
        if (activeResolved) {
            XposedBridge.log("[Vendor] applyManualOverride($id) 被忽略：active 已经求值过，调用时机太晚了")
            return
        }
        manualOverrideId = id
    }

    @Volatile
    private var activeResolved = false

    val active: SystemHookVendor by lazy {
        activeResolved = true
        select(VendorProfile.current, manualOverrideId)
    }

    val isManualOverride: Boolean
        get() = !manualOverrideId.isNullOrBlank() && manualOverrideId != VendorScheme.AUTO.id && ALL.any { it.id == manualOverrideId && it.family == active.family }

    private var logged = false

    internal fun select(profile: VendorProfile, overrideId: String?): SystemHookVendor {
        if (!overrideId.isNullOrBlank() && overrideId != VendorScheme.AUTO.id) {
            val manual = ALL.find { it.id == overrideId }
            if (manual != null) {
                XposedBridge.log("[Vendor] 命中手动覆盖：$overrideId")
                return ALL.filterIsInstance<SystemVersionVendor>()
                    .filter { it.family == manual.family && it.matches(profile) }
                    .maxByOrNull { it.priority } ?: manual
            }
            XposedBridge.log("[Vendor] 手动覆盖 id '$overrideId' 未在 ALL 列表中找到匹配项，回退到自动识别")
        }

        val chosen = ALL.filter {
            try {
                it.matches(profile)
            } catch (t: Throwable) {
                XposedBridge.log("[Vendor] matches() of ${it.id} threw: $t")
                false
            }
        }.maxByOrNull { it.priority } ?: AospVendor
        return chosen
    }

    fun logSelectionOnce() {
        if (logged) return
        logged = true
        try {
            XposedBridge.log("[Vendor] active=${active.id} (family=${active.family}, manual=$isManualOverride) profile=${VendorProfile.current}")
        } catch (_: Throwable) {}
    }

    fun classCandidates(component: SystemComponent): List<String> {
        val vendor = try {
            active.classCandidates(component)
        } catch (t: Throwable) {
            XposedBridge.log("[Vendor] classCandidates(${component}) of ${active.id} threw: $t")
            emptyList()
        }
        return (vendor + AospVendor.classCandidates(component)).distinct()
    }

    fun installExtraHooks(hooker: LocationHooker, classLoader: ClassLoader) {
        try {
            active.installExtraHooks(hooker, classLoader)
        } catch (t: Throwable) {
            XposedBridge.log("[Vendor] installExtraHooks of ${active.id} threw: $t")
        }
    }

    val additionalExemptPackages: Set<String> by lazy {
        try {
            active.additionalExemptPackages()
        } catch (t: Throwable) {
            XposedBridge.log("[Vendor] additionalExemptPackages of ${active.id} threw: $t")
            emptySet()
        }
    }
}
