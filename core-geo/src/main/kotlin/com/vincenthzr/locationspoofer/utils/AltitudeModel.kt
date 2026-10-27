package com.vincenthzr.locationspoofer.utils

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object AltitudeModel {

    const val DEFAULT_VARIATION_M = 10
    const val MAX_VARIATION_M = 100

    private const val MAX_GRADE = 0.08
    private const val MIN_WAVELENGTH_M = 250.0
    private const val WAVELENGTH_SPREAD = 4.0
    private const val COMPONENTS = 5
    private const val TERRAIN_SEED = 0x7E22A1A7L
    private const val DRIFT_SEED = 0x0A171707L
    private const val METERS_PER_DEG_LAT = 110_540.0
    private const val METERS_PER_DEG_LNG = 111_320.0

    private class Terrain(minWavelengthM: Double) {
        private val kx: DoubleArray
        private val ky: DoubleArray
        private val phases: DoubleArray
        private val weights: DoubleArray

        init {
            val rnd = Random(TERRAIN_SEED)
            kx = DoubleArray(COMPONENTS)
            ky = DoubleArray(COMPONENTS)
            for (i in 0 until COMPONENTS) {
                val wavelength = minWavelengthM * WAVELENGTH_SPREAD.pow(rnd.nextDouble())
                val direction = rnd.nextDouble() * 2 * PI
                kx[i] = 2 * PI / wavelength * cos(direction)
                ky[i] = 2 * PI / wavelength * sin(direction)
            }
            phases = DoubleArray(COMPONENTS) { rnd.nextDouble() * 2 * PI }
            val raw = DoubleArray(COMPONENTS) { 0.5 + rnd.nextDouble() }
            val sum = raw.sum()
            weights = DoubleArray(COMPONENTS) { raw[it] / sum }
        }

        fun value(xM: Double, yM: Double): Double {
            var v = 0.0
            for (i in 0 until COMPONENTS) v += weights[i] * sin(kx[i] * xM + ky[i] * yM + phases[i])
            return v
        }
    }

    private val drift = MotionRealism.SmoothNoise(DRIFT_SEED, 30.0, 400.0)

    @Volatile private var cachedTerrain: Pair<Double, Terrain>? = null

    private fun terrain(amplitudeM: Double): Terrain {
        cachedTerrain?.let { (amp, t) -> if (amp == amplitudeM) return t }
        val minWavelength = max(MIN_WAVELENGTH_M, 2 * PI * amplitudeM / MAX_GRADE)
        return Terrain(minWavelength).also { cachedTerrain = amplitudeM to it }
    }

    fun altitude(baseM: Double, variationM: Double, driftM: Double, lat: Double, lng: Double, epochMs: Long): Double {
        val range = variationM.coerceIn(0.0, MAX_VARIATION_M.toDouble())
        if (range <= 0.0) return baseM
        val driftAmp = min(driftM.coerceAtLeast(0.0), range / 2)
        val terrainAmp = range - driftAmp
        val x = lng * METERS_PER_DEG_LNG * cos(lat * PI / 180)
        val y = lat * METERS_PER_DEG_LAT
        return baseM + terrainAmp * terrain(terrainAmp).value(x, y) + driftAmp * drift.value(epochMs / 1000.0)
    }
}
