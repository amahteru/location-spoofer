package io.github.amahteru.locationspoofer.xposed.hooks.vendor

import io.github.amahteru.locationspoofer.xposed.diagnostics.HookStatus
import io.github.amahteru.locationspoofer.xposed.utils.XposedBridge
import io.github.amahteru.locationspoofer.xposed.utils.XposedHelpers

object SystemClassLocator {
    fun locate(
        component: SystemComponent,
        classLoader: ClassLoader,
        extraLoaders: List<ClassLoader?> = emptyList(),
        threadKeywords: List<String> = emptyList(),
        serviceNames: List<String> = emptyList(),
        deepScan: Boolean = false,
        apexJars: List<String> = emptyList(),
    ): Class<*>? {
        val names = VendorRegistry.classCandidates(component)
        fun find(cl: ClassLoader?): Class<*>? = names.firstNotNullOfOrNull { XposedHelpers.findClassIfExists(it, cl) }
        fun found(clazz: Class<*>, source: String): Class<*> {
            XposedBridge.log("[Vendor] $component -> ${clazz.name} (vendor=${VendorRegistry.active.id}, via $source)")
            HookStatus.classFound(component, clazz, source)
            return clazz
        }

        for (cl in listOf(classLoader) + extraLoaders) {
            find(cl)?.let { return found(it, "classLoader") }
        }

        if (threadKeywords.isNotEmpty()) {
            try {
                for (t in Thread.getAllStackTraces().keys) {
                    if (threadKeywords.none { t.name.contains(it, ignoreCase = true) }) continue
                    val cl = t.contextClassLoader ?: continue
                    find(cl)?.let { return found(it, "thread:${t.name}") }
                }
            } catch (t: Throwable) {
                XposedBridge.log("[Vendor] $component thread scan error: $t")
            }
        }

        if (serviceNames.isNotEmpty()) {
            try {
                val sm = XposedHelpers.findClassIfExists("android.os.ServiceManager", classLoader)
                val cache = sm?.let { XposedHelpers.getStaticObjectField(it, "sCache") as? Map<*, *> }
                cache?.forEach { (name, service) ->
                    if (name !in serviceNames) return@forEach
                    val cl = service?.javaClass?.classLoader ?: return@forEach
                    find(cl)?.let { return found(it, "ServiceManager[$name]") }
                }
            } catch (t: Throwable) {
                XposedBridge.log("[Vendor] $component ServiceManager scan error: $t")
            }
        }

        if (deepScan) {
            try {
                val ls = XposedHelpers.findClassIfExists("com.android.server.LocalServices", classLoader)
                val objects = ls?.let { XposedHelpers.getStaticObjectField(it, "sLocalServiceObjects") as? Map<*, *> }
                objects?.forEach { (key, service) ->
                    for (cl in listOf((key as? Class<*>)?.classLoader, service?.javaClass?.classLoader)) {
                        if (cl == null) continue
                        find(cl)?.let { return found(it, "LocalServices") }
                    }
                }
            } catch (t: Throwable) {
                XposedBridge.log("[Vendor] $component LocalServices scan error: $t")
            }

            try {
                val loadersClass = Class.forName("android.app.ApplicationLoaders")
                val instance = loadersClass.getMethod("getDefault").invoke(null)
                for (field in loadersClass.declaredFields) {
                    if (!Map::class.java.isAssignableFrom(field.type)) continue
                    field.isAccessible = true
                    val map = field.get(instance) as? Map<*, *> ?: continue
                    for (cl in map.values) {
                        if (cl !is ClassLoader) continue
                        find(cl)?.let { return found(it, "ApplicationLoaders") }
                    }
                }
            } catch (t: Throwable) {
                XposedBridge.log("[Vendor] $component ApplicationLoaders scan error: $t")
            }
        }

        for (path in apexJars) {
            if (!java.io.File(path).exists()) continue
            try {
                find(dalvik.system.PathClassLoader(path, classLoader))?.let { return found(it, "apex:$path") }
            } catch (t: Throwable) {
                XposedBridge.log("[Vendor] $component load $path failed: $t")
            }
        }

        XposedBridge.log("[Vendor] $component not found, candidates=$names")
        HookStatus.classMissing(component)
        return null
    }
}
