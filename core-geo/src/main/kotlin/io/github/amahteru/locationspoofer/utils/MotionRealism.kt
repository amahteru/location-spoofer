package io.github.amahteru.locationspoofer.utils

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

object MotionRealism {
    enum class Level(
        val id: Int,
        val cadenceVariation: Double,
        val altitudeAmplitudeM: Double,
        val stepAmplitudeVariation: Double,
        val sensorNoise: Double
    ) {
        OFF(0, 0.0, 0.0, 0.0, 0.0),
        LOW(1, 0.015, 1.5, 0.05, 0.03),
        MEDIUM(2, 0.03, 3.0, 0.10, 0.06),
        HIGH(3, 0.05, 5.0, 0.18, 0.10);

        companion object {
            fun fromId(id: Int): Level = entries.firstOrNull { it.id == id } ?: MEDIUM
        }
    }

    const val DEFAULT_LEVEL_ID = 2
    const val DEFAULT_SPEED_FLUCTUATION_PCT = 10
    const val MAX_SPEED_FLUCTUATION_PCT = 30

    private const val CADENCE_SHARE_OF_SPEED = 0.4
    private const val RUN_SPEED_THRESHOLD = 2.2
    private const val GRAVITY = 9.80665f

    private const val SPEED_SALT = 0x5EED5EEDL
    private const val CADENCE_SALT = 0x0CADE0CEL
    private const val STEP_SALT = 0x57E9057EL

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
        private val cadenceNoise = SmoothNoise(startTimestamp xor CADENCE_SALT, 3.0, 60.0)

        fun speed(baseSpeed: Double, elapsedSec: Double): Double =
            baseSpeed * (1 + fluctuation * speedNoise.value(elapsedSec))

        fun distance(baseSpeed: Double, elapsedSec: Double): Double =
            baseSpeed * (elapsedSec + fluctuation * speedNoise.integral(elapsedSec))

        fun cadence(baseCadenceSpm: Double, elapsedSec: Double): Double = baseCadenceSpm * (
            1 + CADENCE_SHARE_OF_SPEED * fluctuation * speedNoise.value(elapsedSec) +
                level.cadenceVariation * cadenceNoise.value(elapsedSec)
            )

        fun steps(baseCadenceSpm: Double, elapsedSec: Double): Double = baseCadenceSpm / 60.0 * (
            elapsedSec + CADENCE_SHARE_OF_SPEED * fluctuation * speedNoise.integral(elapsedSec) +
                level.cadenceVariation * cadenceNoise.integral(elapsedSec)
            )

        fun accelerometer(
            stepsFloat: Double,
            speed: Double,
            noise: Random
        ): FloatArray {
            val stepIndex = floor(stepsFloat).toLong()
            val stepPhase = stepsFloat - stepIndex
            val strength = 1 + level.stepAmplitudeVariation * hashUnit(startTimestamp xor STEP_SALT, stepIndex)
            val out = proceduralGait(stepPhase, stepsFloat, stepIndex, speed, strength)
            if (level.sensorNoise > 0) {
                for (i in 0..2) out[i] += (noise.nextGaussian() * level.sensorNoise).toFloat()
            }
            return out
        }
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

    private const val SHAPE_SAMPLES = 256

    private fun bump(p: Double, center: Double, width: Double): Double {
        var d = abs(p - center)
        if (d > 0.5) d = 1 - d
        return exp(-0.5 * (d / width) * (d / width))
    }

    private fun zeroMeanTable(shape: (Double) -> Double): DoubleArray {
        val table = DoubleArray(SHAPE_SAMPLES) { shape(it.toDouble() / SHAPE_SAMPLES) }
        val mean = table.average()
        for (i in table.indices) table[i] -= mean
        return table
    }

    private val WALK_VERTICAL = zeroMeanTable { p ->
        1.0 * bump(p, 0.00, 0.045) - 0.45 * bump(p, 0.13, 0.07) + 0.55 * bump(p, 0.42, 0.09) - 0.65 * bump(p, 0.72, 0.13)
    }
    private val WALK_FORWARD = zeroMeanTable { p ->
        -0.5 * bump(p, 0.03, 0.05) + 0.45 * bump(p, 0.40, 0.09) - 0.15 * bump(p, 0.75, 0.15)
    }

    private val RUN_VERTICAL = zeroMeanTable { p ->
        1.0 * bump(p, 0.00, 0.035) + 0.55 * bump(p, 0.16, 0.09) - 0.85 * bump(p, 0.62, 0.18)
    }
    private val RUN_FORWARD = zeroMeanTable { p ->
        -0.6 * bump(p, 0.02, 0.04) + 0.5 * bump(p, 0.25, 0.08)
    }

    private fun lookup(table: DoubleArray, phase: Double): Double {
        val pos = (phase - floor(phase)) * table.size
        val i = pos.toInt() % table.size
        val frac = pos - floor(pos)
        return table[i] * (1 - frac) + table[(i + 1) % table.size] * frac
    }

    private fun proceduralGait(
        stepPhase: Double,
        stepsFloat: Double,
        stepIndex: Long,
        speed: Double,
        strength: Double
    ): FloatArray {
        val running = speed >= RUN_SPEED_THRESHOLD
        val amplitude = if (running) 6.0 + 1.5 * speed else 2.0 + 1.0 * speed
        val vertical = lookup(if (running) RUN_VERTICAL else WALK_VERTICAL, stepPhase)
        val forward = lookup(if (running) RUN_FORWARD else WALK_FORWARD, stepPhase)
        val side = if (stepIndex % 2 == 0L) 1.0 else -1.0
        val lateral = 0.12 * amplitude * sin(PI * stepsFloat) + 0.1 * amplitude * side * bump(stepPhase, 0.02, 0.05)

        val z = (GRAVITY + strength * amplitude * vertical).coerceAtLeast(0.0)
        return floatArrayOf(
            (strength * lateral).toFloat(),
            (strength * 0.35 * amplitude * forward).toFloat(),
            z.toFloat()
        )
    }

    private fun hashUnit(seed: Long, n: Long): Double {
        var x = seed xor (n * -0x61c8864680b583ebL)
        x = (x xor (x ushr 33)) * -0xae502812aa7333L
        x = (x xor (x ushr 33)) * -0x3b314601e57a13adL
        x = x xor (x ushr 33)
        return (x ushr 11).toDouble() / (1L shl 53).toDouble() * 2 - 1
    }
}
