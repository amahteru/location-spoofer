package io.github.amahteru.locationspoofer.data.model

import org.json.JSONObject

data class HookStatusReport(
    val process: String,
    val updatedAt: Long,
    val vendorId: String,
    val manualVendor: Boolean,
    val system: String,
    val configPath: String,
    val configModified: Long,
    val components: List<Component>,
    val errors: List<String>
) {
    enum class State {
        HOOKED,
        NO_METHODS,
        NOT_FOUND,
        PENDING
    }

    data class Component(
        val name: String,
        val found: Boolean,
        val searched: Boolean,
        val className: String,
        val source: String,
        val methods: Map<String, Int>
    ) {
        val state: State
            get() = when {
                !found -> if (searched) State.NOT_FOUND else State.PENDING
                methods.values.none { it > 0 } -> State.NO_METHODS
                else -> State.HOOKED
            }
    }

    val hookedCount: Int get() = components.count { it.state == State.HOOKED }

    fun isStale(bootTimeMs: Long): Boolean = updatedAt < bootTimeMs

    companion object {
        fun parse(text: String): HookStatusReport? = try {
            val json = JSONObject(text.trim())
            val vendor = json.optJSONObject("vendor") ?: JSONObject()
            val components = json.optJSONArray("components")
            val errors = json.optJSONArray("errors")
            HookStatusReport(
                process = json.getString("process"),
                updatedAt = json.optLong("updated_at"),
                vendorId = vendor.optString("id"),
                manualVendor = vendor.optBoolean("manual"),
                system = json.optString("system"),
                configPath = json.optJSONObject("config")?.optString("path").orEmpty(),
                configModified = json.optJSONObject("config")?.optLong("modified") ?: 0L,
                components = (0 until (components?.length() ?: 0)).map { i ->
                    val c = components!!.getJSONObject(i)
                    val methods = c.optJSONObject("methods")
                    Component(
                        name = c.getString("name"),
                        found = c.optBoolean("found"),
                        searched = c.optBoolean("searched"),
                        className = c.optString("class"),
                        source = c.optString("source"),
                        methods = methods?.keys()?.asSequence()?.associateWith { methods.optInt(it) }.orEmpty()
                    )
                },
                errors = (0 until (errors?.length() ?: 0)).map { errors!!.getString(it) }
            )
        } catch (_: Exception) {
            null
        }
    }
}
