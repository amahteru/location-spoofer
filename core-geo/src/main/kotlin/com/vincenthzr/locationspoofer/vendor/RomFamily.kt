package com.vincenthzr.locationspoofer.vendor

enum class RomFamily(
    val displayName: String,
    val progressKeywords: List<String>
) {
    AOSP("Android", listOf("AOSP")),

    HYPEROS_MIUI("HyperOS", listOf("HyperOS", "MIUI")),

    COLOROS("ColorOS", listOf("ColorOS", "OxygenOS")),

    ONEUI("One UI", listOf("One UI")),

    ORIGINOS_FUNTOUCH("OriginOS", listOf("OriginOS")),

    MAGICOS_EMUI("MagicOS", listOf("MagicOS", "EMUI")),

    FLYME("Flyme", listOf("Flyme")),
}
