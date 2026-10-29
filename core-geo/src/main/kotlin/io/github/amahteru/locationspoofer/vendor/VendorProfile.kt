package io.github.amahteru.locationspoofer.vendor

data class VendorProfile(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val sdkInt: Int,
    val release: String,
    val props: Map<String, String>,
) {
    fun prop(key: String): String = props[key].orEmpty()
    fun hasProp(key: String): Boolean = props[key]?.isNotBlank() == true

    val family: RomFamily get() = RomRules.familyOf(this)

    val systemLabel: String get() = RomRules.systemLabel(this)

    override fun toString(): String =
        "VendorProfile(manufacturer=$manufacturer, brand=$brand, model=$model, sdk=$sdkInt, release=$release, props=$props)"

    companion object {
        val current: VendorProfile by lazy { detect() }

        private fun detect(): VendorProfile {
            val props = LinkedHashMap<String, String>()
            for (key in RomRules.PROBED_PROPS) {
                val v = readProp(key)
                if (v.isNotBlank()) props[key] = v
            }
            return VendorProfile(
                manufacturer = buildField("android.os.Build", "MANUFACTURER") as? String ?: "",
                brand = buildField("android.os.Build", "BRAND") as? String ?: "",
                model = buildField("android.os.Build", "MODEL") as? String ?: "",
                device = buildField("android.os.Build", "DEVICE") as? String ?: "",
                sdkInt = buildField("android.os.Build\$VERSION", "SDK_INT") as? Int ?: 0,
                release = buildField("android.os.Build\$VERSION", "RELEASE") as? String ?: "",
                props = props,
            )
        }

        private fun buildField(className: String, field: String): Any? = try {
            Class.forName(className).getField(field).get(null)
        } catch (_: Throwable) {
            null
        }

        private fun readProp(key: String): String = try {
            val sp = Class.forName("android.os.SystemProperties")
            (sp.getMethod("get", String::class.java).invoke(null, key) as? String).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }
}
