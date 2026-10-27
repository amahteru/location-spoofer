package com.vincenthzr.locationspoofer.vendor

object RomRules {
    fun isColorOs16(profile: VendorProfile): Boolean {
        val version = profile.prop("ro.build.version.oplusrom")
            .ifBlank { profile.prop("ro.build.version.opporom") }
        val major = version.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.toIntOrNull()
        return profile.family == RomFamily.COLOROS && profile.sdkInt == 36 && major == 16
    }

    val PROBED_PROPS = listOf(
        "ro.mi.os.version.name",
        "ro.mi.os.version.code",
        "ro.miui.ui.version.name",
        "ro.miui.ui.version.code",
        "ro.build.version.oplusrom",
        "ro.build.version.opporom",
        "ro.oplus.image.my_product.type",
        "ro.build.version.oneui",
        "ro.build.version.sem",
        "ro.vivo.os.version",
        "ro.vivo.product.series",
        "ro.build.version.emui",
        "ro.build.version.magic",
        "ro.flyme.published",
        "persist.sys.flyme.pushsdk",
    )

    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")
    private val OPLUS_BRANDS = setOf("oppo", "oneplus", "realme")
    private val VIVO_BRANDS = setOf("vivo", "iqoo")
    private val HONOR_HUAWEI_BRANDS = setOf("huawei", "honor")

    fun familyOf(p: VendorProfile): RomFamily {
        val names = setOf(p.manufacturer.lowercase(), p.brand.lowercase())
        fun brandIn(set: Set<String>) = names.any { it in set }
        return when {
            p.hasProp("ro.mi.os.version.name") || p.hasProp("ro.miui.ui.version.name") || brandIn(XIAOMI_BRANDS) ->
                RomFamily.HYPEROS_MIUI
            p.hasProp("ro.build.version.oplusrom") || p.hasProp("ro.build.version.opporom") || brandIn(OPLUS_BRANDS) ->
                RomFamily.COLOROS
            p.hasProp("ro.build.version.oneui") || brandIn(setOf("samsung")) -> RomFamily.ONEUI
            p.hasProp("ro.vivo.os.version") || brandIn(VIVO_BRANDS) -> RomFamily.ORIGINOS_FUNTOUCH
            p.hasProp("ro.build.version.magic") || p.hasProp("ro.build.version.emui") || brandIn(HONOR_HUAWEI_BRANDS) ->
                RomFamily.MAGICOS_EMUI
            p.hasProp("ro.flyme.published") || brandIn(setOf("meizu")) -> RomFamily.FLYME
            else -> RomFamily.AOSP
        }
    }

    fun romName(p: VendorProfile): String = when (val family = familyOf(p)) {
        RomFamily.HYPEROS_MIUI ->
            if (!p.hasProp("ro.mi.os.version.name") && p.hasProp("ro.miui.ui.version.name")) "MIUI" else family.displayName
        else -> family.displayName
    }

    fun romVersion(p: VendorProfile): String = when (familyOf(p)) {
        RomFamily.HYPEROS_MIUI -> p.prop("ro.mi.os.version.name").removePrefix("OS")
            .ifBlank { p.prop("ro.miui.ui.version.name") }
        RomFamily.COLOROS -> p.prop("ro.build.version.oplusrom").ifBlank { p.prop("ro.build.version.opporom") }
            .removePrefix("V")
        RomFamily.ONEUI -> formatOneUi(p.prop("ro.build.version.oneui"))
        RomFamily.ORIGINOS_FUNTOUCH -> p.prop("ro.vivo.os.version")
        RomFamily.MAGICOS_EMUI -> p.prop("ro.build.version.magic").ifBlank { p.prop("ro.build.version.emui") }
        RomFamily.FLYME, RomFamily.AOSP -> ""
    }

    fun systemLabel(p: VendorProfile): String {
        val android = "Android ${p.release}"
        if (familyOf(p) == RomFamily.AOSP) return android
        val version = romVersion(p)
        return if (version.isBlank()) "${romName(p)} · $android" else "${romName(p)} $version · $android"
    }

    private fun formatOneUi(raw: String): String {
        val v = raw.toIntOrNull() ?: return raw
        return "${v / 10000}.${v % 10000 / 100}"
    }
}
