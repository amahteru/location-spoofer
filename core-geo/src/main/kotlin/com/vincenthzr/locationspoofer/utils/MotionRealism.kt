package com.vincenthzr.locationspoofer.utils

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object MotionRealism {

    enum class Level(
        val id: Int,
        val altitudeAmplitudeM: Double
    ) {
        OFF(0, 0.0),
        LOW(1, 1.5),
        MEDIUM(2, 3.0),
        HIGH(3, 5.0);

        companion object {
            fun fromId(id: Int): Level = entries.firstOrNull { it.id == id } ?: MEDIUM
        }
    }

    const val DEFAULT_LEVEL_ID = 2
    const val DEFAULT_SPEED_FLUCTUATION_PCT = 10
    const val MAX_SPEED_FLUCTUATION_PCT = 30

    private const val SPEED_SALT = 0x5EED5EEDL

    class SmoothNoise(seed: Long, minPeriodSec: Double, maxPeriodSec: Double, components: Int = 4) {
        private val periods: DoubleArray
        private val phases: DoubleArray
        private val weights: DoubleArray

        init {
            val rnd = Random(seed)
            periods = DoubleArray(components) { minPeriodSec * (maxPeriodSec / minPeriodSec).pow(rnd.nextDouble()) }
            phases = DoubleArray(components) { rnd.nextDouble() * 2 * PI }
            val raw = DoubleArray(components) { 0.5 + rnd.nextDouble() }
            val sum = raw.sum()
            weights = DoubleArray(components) { raw[it] / sum }
        }

        fun value(tSec: Double): Double {
            var v = 0.0
            for (i in periods.indices) v += weights[i] * sin(2 * PI * tSec / periods[i] + phases[i])
            return v
        }

        fun integral(tSec: Double): Double {
            var v = 0.0
            for (i in periods.indices) {
                val w = 2 * PI / periods[i]
                v += weights[i] / w * (cos(phases[i]) - cos(w * tSec + phases[i]))
            }
            return v
        }
    }

    class Session(val startTimestamp: Long, val level: Level, speedFluctuationPct: Int) {
        val speedFluctuationPct = speedFluctuationPct.coerceIn(0, MAX_SPEED_FLUCTUATION_PCT)
        private val fluctuation = this.speedFluctuationPct / 100.0
        private val speedNoise = SmoothNoise(startTimestamp xor SPEED_SALT, 20.0, 600.0)

        fun speed(baseSpeed: Double, elapsedSec: Double): Double =
            baseSpeed * (1 + fluctuation * speedNoise.value(elapsedSec))

        fun distance(baseSpeed: Double, elapsedSec: Double): Double =
            baseSpeed * (elapsedSec + fluctuation * speedNoise.integral(elapsedSec))
    }

    @Volatile
    private var cached: Session? = null

    fun session(startTimestamp: Long, levelId: Int, speedFluctuationPct: Int): Session {
        val level = Level.fromId(levelId)
        val pct = speedFluctuationPct.coerceIn(0, MAX_SPEED_FLUCTUATION_PCT)
        val c = cached
        if (c != null && c.startTimestamp == startTimestamp && c.level == level && c.speedFluctuationPct == pct) return c
        return Session(startTimestamp, level, pct).also { cached = it }
    }
}
