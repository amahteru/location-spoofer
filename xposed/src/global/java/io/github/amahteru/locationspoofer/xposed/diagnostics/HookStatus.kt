package io.github.amahteru.locationspoofer.xposed.diagnostics

import io.github.amahteru.locationspoofer.vendor.HookStatusFiles
import io.github.amahteru.locationspoofer.vendor.VendorProfile
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemComponent
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.SystemProcess
import io.github.amahteru.locationspoofer.xposed.hooks.vendor.VendorRegistry
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

object HookStatus {

    const val FORMAT_VERSION = 1
    val generationId: String = java.util.UUID.randomUUID().toString()
    @Volatile private var hotReload = false
    @Volatile private var closed = false
    fun markHotReload() { hotReload = true }
    @Synchronized fun close() {
        closed = true
        if (writerDelegate.isInitialized()) writer.shutdownNow()
    }

    fun reportPaths(process: SystemProcess): List<String> = HookStatusFiles.paths(process.name)

    private data class Located(val className: String, val source: String, val time: Long)

    @Volatile private var process: SystemProcess? = null
    private val located = ConcurrentHashMap<SystemComponent, Located>()
    private val missing = ConcurrentHashMap.newKeySet<SystemComponent>()
    private val methods = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private val errors = java.util.Collections.synchronizedList(ArrayList<String>())

    private val writerDelegate = lazy {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "LocationSpoofer-HookStatus").apply { isDaemon = true }
        }
    }
    private val writer by writerDelegate
    private val writePending = AtomicBoolean(false)

    fun begin(process: SystemProcess) {
        this.process = process
        scheduleWrite()
    }

    fun classFound(component: SystemComponent, clazz: Class<*>, source: String) {
        missing.remove(component)
        located[component] = Located(clazz.name, source, System.currentTimeMillis())
        scheduleWrite()
    }

    fun classMissing(component: SystemComponent) {
        if (!located.containsKey(component)) missing.add(component)
        scheduleWrite()
    }

    fun methodHooked(className: String, methodName: String, count: Int) {
        methods.getOrPut(className) { ConcurrentHashMap() }.merge(methodName, count) { a, b -> maxOf(a, b) }
        if (process != null) scheduleWrite()
    }

    @Volatile private var configPath: String? = null
    @Volatile private var configModified = 0L

    fun configLoaded(path: String, modified: Long) {
        if (path == configPath && modified == configModified) return
        configPath = path
        configModified = modified
        scheduleWrite()
    }

    fun error(where: String, t: Throwable) {
        synchronized(errors) {
            if (errors.size < 20) errors += "$where: ${t.javaClass.simpleName}: ${t.message}"
        }
        scheduleWrite()
    }

    @Synchronized private fun scheduleWrite() {
        if (closed || process == null) return
        if (!writePending.compareAndSet(false, true)) return
        writer.schedule({
            writePending.set(false)
            write()
        }, 1500, TimeUnit.MILLISECONDS)
    }

    private fun write() {
        val proc = process ?: return
        val json = try {
            toJson(proc)
        } catch (t: Throwable) {
            return
        }
        for (path in reportPaths(proc)) {
            try {
                val file = File(path)
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(json.toString())
                if (tmp.renameTo(file)) return
            } catch (_: Throwable) {
            }
        }
    }

    private fun toJson(proc: SystemProcess): JSONObject {
        val profile = VendorProfile.current
        val active = VendorRegistry.active
        val components = JSONArray()
        for (component in SystemComponent.entries.filter { it.process == proc }) {
            val loc = located[component]
            components.put(JSONObject().apply {
                put("name", component.name)
                put("found", loc != null)
                put("searched", loc != null || component in missing)
                if (loc != null) {
                    put("class", loc.className)
                    put("source", loc.source)
                    put("methods", JSONObject(methods[loc.className].orEmpty().toSortedMap() as Map<*, *>))
                }
            })
        }
        return JSONObject().apply {
            put("format", FORMAT_VERSION)
            put("generation", generationId)
            put("load_kind", if (hotReload) "hot-reload" else "initial")
            put("pid", android.os.Process.myPid())
            put("process", proc.name)
            put("updated_at", System.currentTimeMillis())
            put("vendor", JSONObject().apply {
                put("id", active.id)
                put("family", active.family.name)
                put("manual", VendorRegistry.isManualOverride)
            })
            configPath?.let { put("config", JSONObject().put("path", it).put("modified", configModified)) }
            put("system", profile.systemLabel)
            put("model", profile.model)
            put("components", components)
            put("errors", JSONArray(synchronized(errors) { errors.toList() }))
        }
    }
}
