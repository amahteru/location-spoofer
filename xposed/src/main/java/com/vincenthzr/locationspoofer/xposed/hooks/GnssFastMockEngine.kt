package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.utils.XposedBridge
import com.vincenthzr.locationspoofer.xposed.utils.XposedHelpers
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object GnssFastMockEngine {

    private const val MAX_SATELLITES = 32

    private val CONSTELLATION_TYPES = intArrayOf(
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        3, 3, 3, 3,
        6, 6, 6, 6
    )

    private val SVID_LIST = intArrayOf(
        1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 13, 14,
        1, 3, 6, 7, 8, 9, 11, 14, 17, 19, 21, 22,
        1, 2, 3, 4,
        1, 2, 3, 5
    )

    private val BASE_ELEVATIONS = floatArrayOf(
        78f, 65f, 54f, 42f, 38f, 72f, 58f, 49f, 33f, 61f, 45f, 29f,
        82f, 69f, 51f, 44f, 36f, 75f, 63f, 47f, 31f, 59f, 41f, 25f,
        55f, 43f, 35f, 28f,
        60f, 48f, 39f, 22f
    )

    private val BASE_AZIMUTHS = floatArrayOf(
        12f, 45f, 78f, 112f, 145f, 178f, 212f, 245f, 278f, 312f, 335f, 25f,
        35f, 68f, 95f, 132f, 165f, 198f, 232f, 265f, 298f, 325f, 15f, 52f,
        85f, 175f, 265f, 345f,
        45f, 135f, 225f, 315f
    )

    private val JITTER_TABLE = floatArrayOf(
        -0.8f, 0.5f, 1.2f, -0.3f, 0.9f, -1.1f, 0.4f, -0.6f,
        1.1f, -0.9f, 0.7f, -0.4f, 1.3f, -1.2f, 0.3f, -0.2f
    )

    @Volatile
    private var cachedGnssStatusObj: Any? = null
    @Volatile
    private var lastGnssStatusTimeMs: Long = 0L
    @Volatile
    private var cachedGnssCount: Int = 0

    private class ClassReflectionHolder(val classLoader: ClassLoader) {
        var isInitialized = false

        var builderClass: Class<*>? = null
        var builderConstructor: Constructor<*>? = null
        var addSatelliteMethod: Method? = null
        var buildMethod: Method? = null

        var gnssStatusClass: Class<*>? = null
        var countField: Field? = null
        var svidWithFlagsField: Field? = null
        var cn0DbHzField: Field? = null
        var elevationsField: Field? = null
        var azimuthsField: Field? = null
        var carrierFrequenciesField: Field? = null
        var basebandCn0DbHzsField: Field? = null

        var gpsSatelliteClass: Class<*>? = null
        var gpsSatelliteConstructor: Constructor<*>? = null
        var satValidField: Field? = null
        var satHasEphemerisField: Field? = null
        var satHasAlmanacField: Field? = null
        var satUsedInFixField: Field? = null
        var satSnrField: Field? = null
        var satElevationField: Field? = null
        var satAzimuthField: Field? = null

        fun init() {
            if (isInitialized) return
            synchronized(this) {
                if (isInitialized) return

                try {
                    builderClass = XposedHelpers.findClassIfExists(
                        "android.location.GnssStatus\$Builder",
                        classLoader
                    )
                    if (builderClass != null) {
                        builderConstructor = builderClass!!.getDeclaredConstructor().apply { isAccessible = true }
                        addSatelliteMethod = builderClass!!.getDeclaredMethod(
                            "addSatellite",
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType
                        ).apply { isAccessible = true }
                        buildMethod = builderClass!!.getDeclaredMethod("build").apply { isAccessible = true }
                    }
                } catch (e: Throwable) {
                }

                try {
                    gnssStatusClass = XposedHelpers.findClassIfExists("android.location.GnssStatus", classLoader)
                    if (gnssStatusClass != null) {
                        countField = gnssStatusClass!!.getDeclaredField("mSvCount").apply { isAccessible = true }
                        svidWithFlagsField = gnssStatusClass!!.getDeclaredField("mSvidWithFlags").apply { isAccessible = true }
                        cn0DbHzField = gnssStatusClass!!.getDeclaredField("mCn0DbHz").apply { isAccessible = true }
                        elevationsField = gnssStatusClass!!.getDeclaredField("mElevations").apply { isAccessible = true }
                        azimuthsField = gnssStatusClass!!.getDeclaredField("mAzimuths").apply { isAccessible = true }
                        try { carrierFrequenciesField = gnssStatusClass!!.getDeclaredField("mCarrierFrequencies").apply { isAccessible = true } } catch (_: Throwable) {}
                        try { basebandCn0DbHzsField = gnssStatusClass!!.getDeclaredField("mBasebandCn0DbHzs").apply { isAccessible = true } } catch (_: Throwable) {}
                    }
                } catch (e: Throwable) {
                }

                try {
                    gpsSatelliteClass = XposedHelpers.findClassIfExists("android.location.GpsSatellite", classLoader)
                    if (gpsSatelliteClass != null) {
                        gpsSatelliteConstructor = gpsSatelliteClass!!.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
                        try { satValidField = gpsSatelliteClass!!.getDeclaredField("mValid").apply { isAccessible = true } } catch (_: Throwable) {}
                        try { satHasEphemerisField = gpsSatelliteClass!!.getDeclaredField("mHasEphemeris").apply { isAccessible = true } } catch (_: Throwable) {}
                        try { satHasAlmanacField = gpsSatelliteClass!!.getDeclaredField("mHasAlmanac").apply { isAccessible = true } } catch (_: Throwable) {}
                        try { satUsedInFixField = gpsSatelliteClass!!.getDeclaredField("mUsedInFix").apply { isAccessible = true } } catch (_: Throwable) {}
                        try { satSnrField = gpsSatelliteClass!!.getDeclaredField("mSnr").apply { isAccessible = true } } catch (_: Throwable) {}
                        try { satElevationField = gpsSatelliteClass!!.getDeclaredField("mElevation").apply { isAccessible = true } } catch (_: Throwable) {}
                        try { satAzimuthField = gpsSatelliteClass!!.getDeclaredField("mAzimuth").apply { isAccessible = true } } catch (_: Throwable) {}
                    }
                } catch (e: Throwable) {
                }

                isInitialized = true
            }
        }
    }

    private val reflectionHolders = ConcurrentHashMap<ClassLoader, ClassReflectionHolder>()

    private fun getReflectionHolder(classLoader: ClassLoader): ClassReflectionHolder {
        val holder = reflectionHolders.getOrPut(classLoader) { ClassReflectionHolder(classLoader) }
        if (!holder.isInitialized) {
            holder.init()
        }
        return holder
    }

    fun getOrCreateSpoofedGnssStatus(
        classLoader: ClassLoader,
        targetCount: Int,
        enableJitter: Boolean,
        fallbackOriginalObj: Any?
    ): Any? {
        val now = System.currentTimeMillis()
        val count = targetCount.coerceIn(4, MAX_SATELLITES)

        val cached = cachedGnssStatusObj
        if (cached != null && cachedGnssCount == count && now - lastGnssStatusTimeMs < 1000L) {
            return cached
        }

        synchronized(this) {
            if (cachedGnssStatusObj != null && cachedGnssCount == count && now - lastGnssStatusTimeMs < 1000L) {
                return cachedGnssStatusObj
            }

            val holder = getReflectionHolder(classLoader)
            val timeStep = (now / 1000L)

            if (holder.builderConstructor != null && holder.addSatelliteMethod != null && holder.buildMethod != null) {
                try {
                    val builder = holder.builderConstructor!!.newInstance()
                    val addMethod = holder.addSatelliteMethod!!

                    for (i in 0 until count) {
                        val type = CONSTELLATION_TYPES[i]
                        val svid = SVID_LIST[i]
                        val baseElev = BASE_ELEVATIONS[i]
                        val baseAz = BASE_AZIMUTHS[i]

                        val driftStep = ((timeStep + i * 3) % 360).toFloat()
                        val elevation = (baseElev + (driftStep % 10 - 5)).coerceIn(16f, 88f)
                        val azimuth = (baseAz + driftStep * 0.1f) % 360f

                        val jitter = if (enableJitter) JITTER_TABLE[(timeStep.toInt() + i) and 15] else 0f
                        val cn0 = (34f + (elevation / 90f) * 8f + jitter).coerceIn(30f, 43.5f)
                        val usedInFix = elevation > 18f

                        val carrierFreq = when (type) {
                            5 -> 1561098000f
                            3 -> 1602000000f
                            6 -> 1575420000f
                            else -> 1575420000f
                        }
                        val basebandCn0 = (cn0 - 2.5f).coerceAtLeast(26f)

                        addMethod.invoke(
                            builder,
                            type,
                            svid,
                            cn0,
                            elevation,
                            azimuth,
                            true,
                            true,
                            usedInFix,
                            true,
                            carrierFreq,
                            true,
                            basebandCn0
                        )
                    }

                    val newStatus = holder.buildMethod!!.invoke(builder)
                    cachedGnssStatusObj = newStatus
                    lastGnssStatusTimeMs = now
                    cachedGnssCount = count
                    return newStatus
                } catch (e: Throwable) {
                    XposedBridge.log("[GnssFastMockEngine] Builder build failed: $e")
                }
            }

            if (fallbackOriginalObj != null && holder.countField != null && holder.svidWithFlagsField != null) {
                try {
                    holder.countField!!.setInt(fallbackOriginalObj, count)

                    val cn0DbHzs = FloatArray(count)
                    val elevations = FloatArray(count)
                    val azimuths = FloatArray(count)
                    val svidWithFlags = IntArray(count)
                    val carrierFreqs = FloatArray(count)
                    val basebandCn0s = FloatArray(count)

                    for (i in 0 until count) {
                        val type = CONSTELLATION_TYPES[i]
                        val svid = SVID_LIST[i]
                        val baseElev = BASE_ELEVATIONS[i]
                        val baseAz = BASE_AZIMUTHS[i]

                        val driftStep = ((timeStep + i * 3) % 360).toFloat()
                        val elevation = (baseElev + (driftStep % 10 - 5)).coerceIn(16f, 88f)
                        val azimuth = (baseAz + driftStep * 0.1f) % 360f
                        val jitter = if (enableJitter) JITTER_TABLE[(timeStep.toInt() + i) and 15] else 0f
                        val cn0 = (34f + (elevation / 90f) * 8f + jitter).coerceIn(30f, 43.5f)
                        val usedInFix = elevation > 18f

                        val carrierFreq = when (type) {
                            5 -> 1561098000f
                            3 -> 1602000000f
                            6 -> 1575420000f
                            else -> 1575420000f
                        }

                        cn0DbHzs[i] = cn0
                        elevations[i] = elevation
                        azimuths[i] = azimuth
                        carrierFreqs[i] = carrierFreq
                        basebandCn0s[i] = cn0 - 2.5f

                        var flags = 1 or 2
                        if (usedInFix) flags = flags or 4
                        svidWithFlags[i] = (svid shl 8) or (type and 0xF) or (flags shl 4)
                    }

                    holder.svidWithFlagsField!!.set(fallbackOriginalObj, svidWithFlags)
                    holder.cn0DbHzField?.set(fallbackOriginalObj, cn0DbHzs)
                    holder.elevationsField?.set(fallbackOriginalObj, elevations)
                    holder.azimuthsField?.set(fallbackOriginalObj, azimuths)
                    holder.carrierFrequenciesField?.set(fallbackOriginalObj, carrierFreqs)
                    holder.basebandCn0DbHzsField?.set(fallbackOriginalObj, basebandCn0s)

                    cachedGnssStatusObj = fallbackOriginalObj
                    lastGnssStatusTimeMs = now
                    cachedGnssCount = count
                    return fallbackOriginalObj
                } catch (e: Throwable) {
                    XposedBridge.log("[GnssFastMockEngine] Fallback field injection failed: $e")
                }
            }
        }

        return fallbackOriginalObj
    }

}
