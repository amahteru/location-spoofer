package io.github.amahteru.locationspoofer.xposed.hooks

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.amahteru.locationspoofer.xposed.LocationHooker
import io.github.amahteru.locationspoofer.xposed.utils.RouteEngine
import io.github.amahteru.locationspoofer.xposed.utils.XposedHelpers
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object SensorStepHooker {
    data class CapturedSensorListener(
        val listener: Any,
        val sensor: Sensor?,
        val handler: Handler?
    )

    val capturedListeners = CopyOnWriteArrayList<CapturedSensorListener>()
    private val hookedListenerClasses = ConcurrentHashMap<Class<*>, Boolean>()

    private const val INITIAL_BOOT_STEPS = 2350.0
    @Volatile private var stepBase: Double = INITIAL_BOOT_STEPS
    @Volatile private var lastStepsFloat: Double = INITIAL_BOOT_STEPS
    @Volatile private var lastStepInitTime: Long = 0L

    private val noiseRng = java.util.Random()

    private var mockStepCounterSensor: Sensor? = null
    private var mockStepDetectorSensor: Sensor? = null
    private var mockAccelerometerSensor: Sensor? = null
    private var mockGyroscopeSensor: Sensor? = null
    private var mockRotationVectorSensor: Sensor? = null

    private val handleToTypeMap = ConcurrentHashMap<Int, Int>()

    fun hookSensorStepSimulation(classLoader: ClassLoader) {
        val targetClasses = listOf(
            "android.hardware.SensorManager",
            "android.hardware.SystemSensorManager"
        )

        for (className in targetClasses) {
            val managerClass = XposedHelpers.findClassIfExists(className, classLoader) ?: continue

            try {
                XposedHelpers.hookAllMethods(managerClass, "getDefaultSensor") { chain, _ ->
                    val result = chain.proceed(chain.args.toTypedArray())
                    val type = (chain.args.firstOrNull() as? Int) ?: return@hookAllMethods result
                    if (result == null && (type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR || type == Sensor.TYPE_ACCELEROMETER || type == Sensor.TYPE_GYROSCOPE || type == Sensor.TYPE_ROTATION_VECTOR)) {
                        return@hookAllMethods getOrCreateMockSensor(type, classLoader)
                    }
                    return@hookAllMethods result
                }
            } catch (_: Throwable) {}

            try {
                XposedHelpers.hookAllMethods(managerClass, "getSensorList") { chain, _ ->
                    val result = chain.proceed(chain.args.toTypedArray())
                    val type = (chain.args.firstOrNull() as? Int) ?: return@hookAllMethods result
                    if (type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR || type == Sensor.TYPE_ACCELEROMETER || type == Sensor.TYPE_GYROSCOPE || type == Sensor.TYPE_ROTATION_VECTOR) {
                        if (result is List<*> && result.isEmpty()) {
                            val mock = getOrCreateMockSensor(type, classLoader)
                            if (mock != null) return@hookAllMethods listOf(mock)
                        }
                    }
                    return@hookAllMethods result
                }
            } catch (_: Throwable) {}

            val regMethods = listOf("registerListener", "registerListenerImpl")
            for (methodName in regMethods) {
                try {
                    XposedHelpers.hookAllMethods(managerClass, methodName) { chain, _ ->
                        val args = chain.args
                        var listener: Any? = null
                        var sensor: Sensor? = null
                        var handler: Handler? = null

                        for (arg in args) {
                            if (arg != null) {
                                if (arg is SensorEventListener || LocationHooker.hasTypeByName(arg.javaClass, "android.hardware.SensorEventListener")) {
                                    listener = arg
                                } else if (arg is Sensor) {
                                    sensor = arg
                                } else if (arg is Handler) {
                                    handler = arg
                                }
                            }
                        }

                        if (listener != null) {
                            val targetSensor = sensor ?: getOrCreateMockSensor(Sensor.TYPE_STEP_COUNTER, classLoader)
                            if (targetSensor != null && (targetSensor.type == Sensor.TYPE_STEP_COUNTER || targetSensor.type == Sensor.TYPE_STEP_DETECTOR || targetSensor.type == Sensor.TYPE_ACCELEROMETER || targetSensor.type == Sensor.TYPE_GYROSCOPE || targetSensor.type == Sensor.TYPE_ROTATION_VECTOR)) {
                                val entry = CapturedSensorListener(listener, targetSensor, handler)
                                if (!capturedListeners.any { it.listener === listener && it.sensor?.type == targetSensor.type }) {
                                    capturedListeners.add(entry)
                                }

                                val handle = getSensorHandle(targetSensor)
                                if (handle != null) {
                                    handleToTypeMap[handle] = targetSensor.type
                                }

                                hookConcreteListenerClass(listener.javaClass, classLoader)
                            }
                        }

                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (_: Throwable) {}
            }

            val unregMethods = listOf("unregisterListener", "unregisterListenerImpl")
            for (methodName in unregMethods) {
                try {
                    XposedHelpers.hookAllMethods(managerClass, methodName) { chain, _ ->
                        val args = chain.args
                        val listener = args.firstOrNull { it != null && (it is SensorEventListener || LocationHooker.hasTypeByName(it.javaClass, "android.hardware.SensorEventListener")) }
                        if (listener != null) {
                            capturedListeners.removeAll { it.listener === listener }
                        }
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (_: Throwable) {}
            }
        }

        hookSensorEventQueue(classLoader)
    }

    private fun hookSensorEventQueue(classLoader: ClassLoader) {
        try {
            val eventQueueClass = XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager\$SensorEventQueue", classLoader)
                ?: XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager\$BaseEventQueue", classLoader)
                ?: return

            XposedHelpers.hookAllMethods(eventQueueClass, "dispatchSensorEvent") { chain, _ ->
                val config = (XposedHelpers.module as? LocationHooker)?.readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("enable_step_simulation", true)) {
                    val handle = chain.args.firstOrNull() as? Int
                    val values = chain.args.getOrNull(1) as? FloatArray

                    if (handle != null && values != null) {
                        val sensorType = handleToTypeMap[handle]
                        val speed = config.optDouble("speed_m_s", 0.0)

                        if (sensorType == Sensor.TYPE_STEP_COUNTER) {
                            val curSteps = calculateCurrentSteps(config)
                            values[0] = curSteps.toFloat()
                        } else if (sensorType == Sensor.TYPE_STEP_DETECTOR) {
                            if (speed > 0.1) {
                                values[0] = 1.0f
                            }
                        } else if (sensorType == Sensor.TYPE_ACCELEROMETER && values.size >= 3) {
                            if (speed > 0.1) {
                                applySyntheticVibration(values, config, speed)
                            }
                        } else if (sensorType == Sensor.TYPE_GYROSCOPE && values.size >= 3) {
                            if (speed > 0.1) {
                                applySyntheticGyroscope(values, config, speed)
                            }
                        } else if (sensorType == Sensor.TYPE_ROTATION_VECTOR && values.size >= 3) {
                            if (speed > 0.1) {
                                applySyntheticRotationVector(values, config, speed)
                            }
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    internal fun saveReloadState(): Array<Any?> = arrayOf(
        capturedListeners.map { arrayOf<Any?>(it.listener, it.sensor, it.handler) }.toTypedArray(),
        stepBase, lastStepsFloat, lastStepInitTime,
        mockStepCounterSensor, mockStepDetectorSensor, mockAccelerometerSensor,
        handleToTypeMap.map { arrayOf(it.key, it.value) }.toTypedArray(),
        mockGyroscopeSensor, mockRotationVectorSensor
    )

    internal fun clearReloadState() { capturedListeners.clear(); hookedListenerClasses.clear() }

    internal fun restoreReloadState(value: Any?) {
        val state = value as? Array<*> ?: return
        (state.getOrNull(0) as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach {
            capturedListeners.add(CapturedSensorListener(it[0]!!, it[1] as? Sensor, it[2] as? Handler))
            hookConcreteListenerClass(it[0]!!.javaClass, it[0]!!.javaClass.classLoader ?: ClassLoader.getSystemClassLoader())
        }
        stepBase = state[1] as Double
        lastStepsFloat = state[2] as Double
        lastStepInitTime = state[3] as Long
        mockStepCounterSensor = state[4] as? Sensor
        mockStepDetectorSensor = state[5] as? Sensor
        mockAccelerometerSensor = state[6] as? Sensor
        (state[7] as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach {
            handleToTypeMap[it[0] as Int] = it[1] as Int
        }
        mockGyroscopeSensor = state.getOrNull(8) as? Sensor
        mockRotationVectorSensor = state.getOrNull(9) as? Sensor
    }

    private fun hookConcreteListenerClass(clazz: Class<*>, classLoader: ClassLoader) {
        if (hookedListenerClasses.putIfAbsent(clazz, true) == null) {
            try {
                XposedHelpers.hookAllMethods(clazz, "onSensorChanged") { chain, _ ->
                    val event = chain.args.firstOrNull() as? SensorEvent
                    if (event != null && event.sensor != null) {
                        val config = (XposedHelpers.module as? LocationHooker)?.readConfig()
                        if (config != null && config.optBoolean("active", false) && config.optBoolean("enable_step_simulation", true)) {
                            val speed = config.optDouble("speed_m_s", 0.0)
                            if (event.sensor.type == Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
                                val curSteps = calculateCurrentSteps(config)
                                event.values[0] = curSteps.toFloat()
                                event.timestamp = SystemClock.elapsedRealtimeNanos()
                            } else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR && event.values.isNotEmpty()) {
                                if (speed > 0.1) {
                                    event.values[0] = 1.0f
                                    event.timestamp = SystemClock.elapsedRealtimeNanos()
                                }
                            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3 && speed > 0.1) {
                                applySyntheticVibration(event.values, config, speed)
                                event.timestamp = SystemClock.elapsedRealtimeNanos()
                            } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE && event.values.size >= 3 && speed > 0.1) {
                                applySyntheticGyroscope(event.values, config, speed)
                                event.timestamp = SystemClock.elapsedRealtimeNanos()
                            } else if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR && event.values.size >= 3 && speed > 0.1) {
                                applySyntheticRotationVector(event.values, config, speed)
                                event.timestamp = SystemClock.elapsedRealtimeNanos()
                            }
                        }
                    }
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}
        }
    }

    private fun currentMotionContext(config: JSONObject, speed: Double): Pair<Double, Double> {
        val now = System.currentTimeMillis()
        val stepsFloat = calculateCurrentStepsFloat(config, now)
        val motionSpeed = RouteEngine.calculateCurrentPosition(config, now).speed.toDouble().takeIf { it > 0.0 } ?: speed
        return stepsFloat to motionSpeed
    }

    private fun applySyntheticVibration(values: FloatArray, config: JSONObject, speed: Double) {
        val (stepsFloat, motionSpeed) = currentMotionContext(config, speed)
        val synthetic = RouteEngine.realismSession(config).accelerometer(stepsFloat, motionSpeed, noiseRng)
        values[0] = synthetic[0]
        values[1] = synthetic[1]
        values[2] = synthetic[2]
    }

    private fun applySyntheticGyroscope(values: FloatArray, config: JSONObject, speed: Double) {
        val (stepsFloat, motionSpeed) = currentMotionContext(config, speed)
        val synthetic = RouteEngine.realismSession(config).gyroscope(stepsFloat, motionSpeed, noiseRng)
        values[0] = synthetic[0]
        values[1] = synthetic[1]
        values[2] = synthetic[2]
    }

    private fun applySyntheticRotationVector(values: FloatArray, config: JSONObject, speed: Double) {
        val (stepsFloat, motionSpeed) = currentMotionContext(config, speed)
        val synthetic = RouteEngine.realismSession(config).rotationVector(stepsFloat, motionSpeed, noiseRng)
        System.arraycopy(synthetic, 0, values, 0, minOf(values.size, synthetic.size))
    }

    fun calculateCurrentSteps(config: JSONObject, now: Long = System.currentTimeMillis()): Long =
        calculateCurrentStepsFloat(config, now).toLong()

    private fun calculateCurrentStepsFloat(config: JSONObject, now: Long): Double {
        val startTime = config.optLong("start_timestamp", now)
        if (lastStepInitTime != startTime) {
            lastStepInitTime = startTime
            stepBase = lastStepsFloat
        }

        val speed = config.optDouble("speed_m_s", 0.0)
        if (speed <= 0.05) return lastStepsFloat

        val elapsedSec = ((now - startTime).coerceAtLeast(0L)) / 1000.0
        val baseCadence = calculateAutoCadence(speed)

        val steps = stepBase + RouteEngine.realismSession(config, startTime).steps(baseCadence.toDouble(), elapsedSec)
        lastStepsFloat = steps
        return steps
    }

    fun calculateAutoCadence(speedMs: Double): Int {
        return when {
            speedMs <= 0.8 -> 95
            speedMs <= 1.5 -> (100 + (speedMs - 0.8) / 0.7 * 25).toInt()
            speedMs <= 2.8 -> (135 + (speedMs - 1.5) / 1.3 * 25).toInt()
            speedMs <= 4.0 -> (160 + (speedMs - 2.8) / 1.2 * 20).toInt()
            speedMs <= 5.5 -> (180 + (speedMs - 4.0) / 1.5 * 15).toInt()
            else -> 200
        }
    }

    private fun deliverSensorEvent(entry: CapturedSensorListener, values: FloatArray) {
        val sensor = entry.sensor ?: return
        val event = createSensorEvent(sensor, values) ?: return
        val targetHandler = entry.handler ?: Handler(Looper.getMainLooper())
        targetHandler.post {
            try {
                if (entry.listener is SensorEventListener) {
                    entry.listener.onSensorChanged(event)
                } else {
                    XposedHelpers.callMethod(entry.listener, "onSensorChanged", event)
                }
            } catch (_: Throwable) {}
        }
    }

    fun dispatchStepEvents(config: JSONObject, classLoader: ClassLoader) {
        if (!config.optBoolean("active", false) || !config.optBoolean("enable_step_simulation", true)) return
        val speed = config.optDouble("speed_m_s", 0.0)
        if (speed <= 0.05) return

        val listeners = capturedListeners.toList()
        if (listeners.isEmpty()) return

        val now = System.currentTimeMillis()
        val totalSteps = calculateCurrentSteps(config, now)
        val stepsFloat = calculateCurrentStepsFloat(config, now)
        val motionSpeed = RouteEngine.calculateCurrentPosition(config, now).speed.toDouble().takeIf { it > 0.0 } ?: speed
        val session = RouteEngine.realismSession(config)

        val accel = session.accelerometer(stepsFloat, motionSpeed, noiseRng)
        val gyro = session.gyroscope(stepsFloat, motionSpeed, noiseRng)
        val rotVec = session.rotationVector(stepsFloat, motionSpeed, noiseRng)

        for (entry in listeners) {
            val sensor = entry.sensor ?: continue
            when (sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> deliverSensorEvent(entry, floatArrayOf(totalSteps.toFloat()))
                Sensor.TYPE_STEP_DETECTOR -> if (speed > 0.1) deliverSensorEvent(entry, floatArrayOf(1.0f))
                Sensor.TYPE_ACCELEROMETER -> if (speed > 0.1) deliverSensorEvent(entry, accel)
                Sensor.TYPE_GYROSCOPE -> if (speed > 0.1) deliverSensorEvent(entry, gyro)
                Sensor.TYPE_ROTATION_VECTOR -> if (speed > 0.1) deliverSensorEvent(entry, rotVec)
            }
        }
    }

    private fun getSensorHandle(sensor: Sensor): Int? {
        return try {
            val handleField = Sensor::class.java.getDeclaredField("mHandle")
            handleField.isAccessible = true
            handleField.getInt(sensor)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getOrCreateMockSensor(type: Int, classLoader: ClassLoader): Sensor? {
        if (type == Sensor.TYPE_STEP_COUNTER && mockStepCounterSensor != null) return mockStepCounterSensor
        if (type == Sensor.TYPE_STEP_DETECTOR && mockStepDetectorSensor != null) return mockStepDetectorSensor
        if (type == Sensor.TYPE_ACCELEROMETER && mockAccelerometerSensor != null) return mockAccelerometerSensor
        if (type == Sensor.TYPE_GYROSCOPE && mockGyroscopeSensor != null) return mockGyroscopeSensor
        if (type == Sensor.TYPE_ROTATION_VECTOR && mockRotationVectorSensor != null) return mockRotationVectorSensor

        try {
            val sensorClass = Class.forName("android.hardware.Sensor", false, classLoader)
            val constructor: Constructor<*> = sensorClass.getDeclaredConstructor()
            constructor.isAccessible = true
            val sensor = constructor.newInstance() as Sensor

            setSensorField(sensor, "mType", type)
            setSensorField(sensor, "mName", when(type) {
                Sensor.TYPE_STEP_COUNTER -> "Step Counter Sensor"
                Sensor.TYPE_STEP_DETECTOR -> "Step Detector Sensor"
                Sensor.TYPE_GYROSCOPE -> "Gyroscope Sensor"
                Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector Sensor"
                else -> "Accelerometer Sensor"
            })
            setSensorField(sensor, "mVendor", "Android")
            setSensorField(sensor, "mVersion", 1)
            setSensorField(sensor, "mResolution", 1.0f)
            setSensorField(sensor, "mPower", 0.05f)

            when (type) {
                Sensor.TYPE_STEP_COUNTER -> mockStepCounterSensor = sensor
                Sensor.TYPE_STEP_DETECTOR -> mockStepDetectorSensor = sensor
                Sensor.TYPE_ACCELEROMETER -> mockAccelerometerSensor = sensor
                Sensor.TYPE_GYROSCOPE -> mockGyroscopeSensor = sensor
                Sensor.TYPE_ROTATION_VECTOR -> mockRotationVectorSensor = sensor
            }
            return sensor
        } catch (_: Throwable) {
            return null
        }
    }

    private fun setSensorField(sensor: Sensor, fieldName: String, value: Any) {
        try {
            val field: Field = Sensor::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(sensor, value)
        } catch (_: Throwable) {}
    }

    private fun createSensorEvent(sensor: Sensor, values: FloatArray): SensorEvent? {
        try {
            val eventConstructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            eventConstructor.isAccessible = true
            val event = eventConstructor.newInstance(values.size) as SensorEvent
            System.arraycopy(values, 0, event.values, 0, values.size)

            val sensorField = SensorEvent::class.java.getDeclaredField("sensor")
            sensorField.isAccessible = true
            sensorField.set(event, sensor)

            val timestampField = SensorEvent::class.java.getDeclaredField("timestamp")
            timestampField.isAccessible = true
            timestampField.setLong(event, SystemClock.elapsedRealtimeNanos())

            val accuracyField = SensorEvent::class.java.getDeclaredField("accuracy")
            accuracyField.isAccessible = true
            accuracyField.setInt(event, 3)

            return event
        } catch (_: Throwable) {
            try {
                val constructors = SensorEvent::class.java.declaredConstructors
                for (ctor in constructors) {
                    ctor.isAccessible = true
                    val paramTypes = ctor.parameterTypes
                    val args = arrayOfNulls<Any>(paramTypes.size)
                    for (i in args.indices) {
                        if (paramTypes[i] == Int::class.javaPrimitiveType) args[i] = values.size
                    }
                    val event = ctor.newInstance(*args) as SensorEvent
                    System.arraycopy(values, 0, event.values, 0, values.size)
                    try {
                        val sf = SensorEvent::class.java.getDeclaredField("sensor")
                        sf.isAccessible = true
                        sf.set(event, sensor)
                        val tf = SensorEvent::class.java.getDeclaredField("timestamp")
                        tf.isAccessible = true
                        tf.setLong(event, SystemClock.elapsedRealtimeNanos())
                        val af = SensorEvent::class.java.getDeclaredField("accuracy")
                        af.isAccessible = true
                        af.setInt(event, 3)
                    } catch (_: Throwable) {}
                    return event
                }
            } catch (_: Throwable) {}

            try {
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
                theUnsafeField.isAccessible = true
                val unsafe = theUnsafeField.get(null)
                val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
                val event = allocateMethod.invoke(unsafe, SensorEvent::class.java) as SensorEvent

                val valuesField = SensorEvent::class.java.getDeclaredField("values")
                valuesField.isAccessible = true
                val arr = FloatArray(values.size)
                System.arraycopy(values, 0, arr, 0, values.size)
                valuesField.set(event, arr)

                val sf = SensorEvent::class.java.getDeclaredField("sensor")
                sf.isAccessible = true
                sf.set(event, sensor)

                val tf = SensorEvent::class.java.getDeclaredField("timestamp")
                tf.isAccessible = true
                tf.setLong(event, SystemClock.elapsedRealtimeNanos())

                val af = SensorEvent::class.java.getDeclaredField("accuracy")
                af.isAccessible = true
                af.setInt(event, 3)
                return event
            } catch (_: Throwable) {}

            return null
        }
    }
}
