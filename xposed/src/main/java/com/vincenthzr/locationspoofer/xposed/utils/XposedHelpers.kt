package com.vincenthzr.locationspoofer.xposed.utils

import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

object XposedHelpers {
    lateinit var module: XposedModule

    fun isModuleInitialized(): Boolean = ::module.isInitialized

    private val fieldCache = ConcurrentHashMap<String, Field?>()
    private val methodCache = ConcurrentHashMap<String, Method?>()

    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        return Class.forName(className, false, classLoader ?: ClassLoader.getSystemClassLoader())
    }

    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        return try {
            findClass(className, classLoader)
        } catch (e: Throwable) {
            null
        }
    }

    private fun findFieldInternal(clazz: Class<*>, fieldName: String): Field? {
        val key = "${clazz.name}#$fieldName"
        if (fieldCache.containsKey(key)) {
            return fieldCache[key]
        }
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                fieldCache[key] = f
                return f
            } catch (e: NoSuchFieldException) {
                c = c.superclass
            }
        }
        fieldCache[key] = null
        return null
    }

    fun getObjectField(obj: Any, fieldName: String): Any? {
        val field = findFieldInternal(obj.javaClass, fieldName)
            ?: throw NoSuchFieldException(fieldName)
        return field.get(obj)
    }

    fun getIntField(obj: Any, fieldName: String): Int {
        val field = findFieldInternal(obj.javaClass, fieldName)
            ?: throw NoSuchFieldException(fieldName)
        return field.getInt(obj)
    }

    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? {
        val field = findFieldInternal(clazz, fieldName)
            ?: throw NoSuchFieldException(fieldName)
        return field.get(null)
    }

    fun setObjectField(obj: Any, fieldName: String, value: Any?) {
        val field = findFieldInternal(obj.javaClass, fieldName)
            ?: throw NoSuchFieldException(fieldName)
        field.set(obj, value)
    }

    fun setIntField(obj: Any, fieldName: String, value: Int) {
        val field = findFieldInternal(obj.javaClass, fieldName)
            ?: throw NoSuchFieldException(fieldName)
        field.setInt(obj, value)
    }

    fun setBooleanField(obj: Any, fieldName: String, value: Boolean) {
        val field = findFieldInternal(obj.javaClass, fieldName)
            ?: throw NoSuchFieldException(fieldName)
        field.setBoolean(obj, value)
    }

    fun setLongField(obj: Any, fieldName: String, value: Long) {
        val field = findFieldInternal(obj.javaClass, fieldName)
            ?: throw NoSuchFieldException(fieldName)
        field.setLong(obj, value)
    }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        val key = "${obj.javaClass.name}#$methodName#${args.size}"
        val cachedMethod = methodCache[key]
        if (cachedMethod != null) {
            return cachedMethod.invoke(obj, *args)
        }
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            for (m in clazz.declaredMethods) {
                if (m.name == methodName && m.parameterCount == args.size) {
                    m.isAccessible = true
                    methodCache[key] = m
                    return m.invoke(obj, *args)
                }
            }
            clazz = clazz.superclass
        }
        throw NoSuchMethodException(methodName)
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        val key = "${clazz.name}#$methodName#${args.size}#static"
        val cachedMethod = methodCache[key]
        if (cachedMethod != null) {
            return cachedMethod.invoke(null, *args)
        }
        var c: Class<*>? = clazz
        while (c != null) {
            for (m in c.declaredMethods) {
                if (m.name == methodName && m.parameterCount == args.size && Modifier.isStatic(
                        m.modifiers
                    )
                ) {
                    m.isAccessible = true
                    methodCache[key] = m
                    return m.invoke(null, *args)
                }
            }
            c = c.superclass
        }
        throw NoSuchMethodException(methodName)
    }

    fun findMethodExact(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val m = c.getDeclaredMethod(methodName, *parameterTypes)
                m.isAccessible = true
                return m
            } catch (e: NoSuchMethodException) {
                c = c.superclass
            }
        }
        throw NoSuchMethodException(methodName)
    }

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        for (c in clazz.declaredConstructors) {
            if (c.parameterCount == args.size) {
                c.isAccessible = true
                return c.newInstance(*args)
            }
        }
        throw NoSuchMethodException("Constructor for " + clazz.name + " not found")
    }

    inline fun hookAllMethods(
        clazz: Class<*>,
        methodName: String,
        crossinline interceptor: (io.github.libxposed.api.XposedInterface.Chain, Executable) -> Any?
    ): List<io.github.libxposed.api.XposedInterface.HookHandle> {
        val handles = mutableListOf<io.github.libxposed.api.XposedInterface.HookHandle>()
        var hooked = 0
        for (method in clazz.declaredMethods) {
            if (method.name == methodName && !Modifier.isAbstract(method.modifiers)) {
                try {
                    val handle = module.hook(method)
                        .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                            override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                                return interceptor(chain, method)
                            }
                        })
                    handles.add(handle)
                    hooked++
                } catch (e: Throwable) {
                    com.vincenthzr.locationspoofer.xposed.diagnostics.HookStatus.error("${clazz.name}#$methodName", e)
                }
            }
        }
        com.vincenthzr.locationspoofer.xposed.diagnostics.HookStatus.methodHooked(clazz.name, methodName, hooked)
        return handles
    }

}

object XposedBridge {
    private val openCellLogLastTimes = ConcurrentHashMap<String, Long>()

    fun log(msg: String) {
        try {
            android.util.Log.i("LocationSpoofer", msg)
        } catch (_: Throwable) {}
        try {
            if (XposedHelpers.isModuleInitialized()) {
                XposedHelpers.module.log(android.util.Log.INFO, "LocationSpoofer", msg)
            }
        } catch (_: Throwable) {}
    }
    fun logOpenCellId(msg: String) {
        log(msg)
    }
    fun logOpenCellId(msg: String, t: Throwable) {
        try {
            android.util.Log.e("LocationSpoofer", msg, t)
        } catch (_: Throwable) {}
    }
    fun logOpenCellIdEvery(key: String, msg: String, intervalMs: Long = 5_000L) {
        val now = System.currentTimeMillis()
        val last = openCellLogLastTimes[key] ?: 0L
        if (now - last > intervalMs) {
            openCellLogLastTimes[key] = now
            try {
                android.util.Log.i("LocationSpoofer", "[$key] $msg")
            } catch (_: Throwable) {}
        }
    }
    fun log(t: Throwable) {
        try {
            android.util.Log.e("LocationSpoofer", "Exception", t)
        } catch (_: Throwable) {}
        try {
            if (XposedHelpers.isModuleInitialized()) {
                XposedHelpers.module.log(android.util.Log.ERROR, "LocationSpoofer", t.message ?: "Exception", t)
            }
        } catch (_: Throwable) {}
    }
}

