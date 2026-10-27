package com.vincenthzr.locationspoofer.vendor

enum class VendorScheme(val id: String) {
    AUTO("auto"),
    HYPEROS("hyperos"),
    COLOROS("coloros"),
    ONEUI("oneui"),
    AOSP("aosp");

    companion object {
        fun fromId(id: String?): VendorScheme = entries.firstOrNull { it.id == id } ?: AUTO

        fun forFamily(family: RomFamily): VendorScheme = when (family) {
            RomFamily.HYPEROS_MIUI -> HYPEROS
            RomFamily.COLOROS -> COLOROS
            RomFamily.ONEUI -> ONEUI
            RomFamily.ORIGINOS_FUNTOUCH,
            RomFamily.MAGICOS_EMUI,
            RomFamily.FLYME,
            RomFamily.AOSP -> AOSP
        }
    }
}
