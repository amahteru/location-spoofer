package com.vincenthzr.locationspoofer.utils

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 运动真实度：给路线模拟的速度和海拔加上自然起伏。
 *
 * 速度是以模拟开始时间为种子的确定性函数——system_server、电话、蓝牙进程和 App 自己的路线进度
 * 都在各自独立计算位置，必须对同一时刻算出同样的结果。随机强度为 [Level.OFF] 且速度浮动为 0 时退化为匀速。
 */
object MotionRealism {

    enum class Level(
        val id: Int,
        /** 海拔随时间漂移的幅度（米），由 [AltitudeModel] 使用 */
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

    /**
     * 若干个周期、相位随机的正弦叠加成的平滑噪声，取值范围 [-1, 1]。
     * 用正弦叠加而不是随机游走，是因为它的积分有解析解：任意时刻的累计距离都能直接算出来，
     * 不依赖逐帧累加的中间状态，所以各个进程在任意时刻都能独立得到一致的结果。
     */
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

        /** ∫₀ᵗ value(τ) dτ */
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

        /** 从开始到 elapsedSec 的累计行进距离（米） */
        fun distance(baseSpeed: Double, elapsedSec: Double): Double =
            baseSpeed * (elapsedSec + fluctuation * speedNoise.integral(elapsedSec))
    }

    @Volatile
    private var cached: Session? = null

    /** 同一次模拟会话内复用同一个 Session，避免在高频 Hook 里反复初始化噪声参数 */
    fun session(startTimestamp: Long, levelId: Int, speedFluctuationPct: Int): Session {
        val level = Level.fromId(levelId)
        val pct = speedFluctuationPct.coerceIn(0, MAX_SPEED_FLUCTUATION_PCT)
        val c = cached
        if (c != null && c.startTimestamp == startTimestamp && c.level == level && c.speedFluctuationPct == pct) return c
        return Session(startTimestamp, level, pct).also { cached = it }
    }
}
