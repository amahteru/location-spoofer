package io.github.amahteru.locationspoofer.utils

import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionRealismTest {
    private val start = 1_789_398_037_217L

    @Test
    fun `level off with no speed fluctuation keeps the legacy uniform motion`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)
        assertEquals(3.0 * 125.0, s.distance(3.0, 125.0), 1e-9)
        assertEquals(3.0, s.speed(3.0, 125.0), 1e-12)
        assertEquals(165.0 / 60.0 * 125.0, s.steps(165.0, 125.0), 1e-9)
    }

    @Test
    fun `sessions with the same start timestamp agree across processes`() {
        val a = MotionRealism.Session(start, MotionRealism.Level.HIGH, 20)
        val b = MotionRealism.Session(start, MotionRealism.Level.HIGH, 20)
        val c = MotionRealism.Session(start + 1, MotionRealism.Level.HIGH, 20)
        assertEquals(a.distance(3.0, 777.0), b.distance(3.0, 777.0), 0.0)
        assertEquals(a.steps(160.0, 777.0), b.steps(160.0, 777.0), 0.0)
        assertNotEquals(a.distance(3.0, 777.0), c.distance(3.0, 777.0), 1e-6)
    }

    @Test
    fun `speed stays within the configured fluctuation and matches the distance derivative`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.MEDIUM, 20)
        var sawVariation = false
        for (t in 0..3600 step 7) {
            val v = s.speed(3.0, t.toDouble())
            assertTrue("speed $v at $t", v in 3.0 * 0.8 - 1e-9..3.0 * 1.2 + 1e-9)
            if (abs(v - 3.0) > 0.1) sawVariation = true
            val h = 1e-3
            val derivative = (s.distance(3.0, t + h) - s.distance(3.0, t.toDouble())) / h
            assertEquals(v, derivative, 1e-3)
        }
        assertTrue(sawVariation)
    }

    @Test
    fun `step count keeps increasing and cadence varies around the base`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.HIGH, 15)
        var last = -1.0
        var minCadence = Double.MAX_VALUE
        var maxCadence = 0.0
        for (t in 0..1800) {
            val steps = s.steps(170.0, t.toDouble())
            assertTrue(steps > last)
            last = steps
            val c = s.cadence(170.0, t.toDouble())
            minCadence = minOf(minCadence, c)
            maxCadence = maxOf(maxCadence, c)
        }
        assertTrue(maxCadence - minCadence > 5)
        assertTrue(minCadence > 170 * 0.85 && maxCadence < 170 * 1.15)
    }

    @Test
    fun `procedural gait averages to gravity over whole cycles`() {
        for (speed in listOf(1.3, 3.2)) {
            val s = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)
            val rnd = Random(1)
            val n = 20_000
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            for (i in 0 until n) {
                val stepsFloat = i * 40.0 / n
                val a = s.accelerometer(stepsFloat, speed, rnd)
                sx += a[0]; sy += a[1]; sz += a[2]
            }
            assertEquals(0.0, sx / n, 0.05)
            assertEquals(0.0, sy / n, 0.05)
            assertEquals(9.80665, sz / n, 0.05)
        }
    }

    @Test
    fun `step impact strength differs between steps when randomness is on`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.MEDIUM, 0)
        val quiet = Random(0)
        val peaks = (0 until 6).map { step -> s.accelerometer(step.toDouble(), 1.4, quiet)[2] }
        assertTrue(peaks.toSet().size > 1)
    }

    @Test
    fun `session cache returns the same instance for the same parameters`() {
        val a = MotionRealism.session(start, 2, 10)
        val b = MotionRealism.session(start, 2, 10)
        val c = MotionRealism.session(start, 2, 20)
        assertTrue(a === b)
        assertTrue(a !== c)
    }

    @Test
    fun `gyroscope produces non-zero periodic angular velocity when running`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.MEDIUM, 0)
        val rnd = Random(42)
        val samples = (0 until 20).map { i -> s.gyroscope(i * 0.1, 3.5, rnd) }
        assertTrue(samples.any { abs(it[0]) > 0.1f })
        assertTrue(samples.any { abs(it[1]) > 0.1f })
        assertTrue(samples.any { abs(it[2]) > 0.1f })
    }

    @Test
    fun `rotation vector is valid unit quaternion`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.MEDIUM, 0)
        val rnd = Random(42)
        for (i in 0 until 50) {
            val q = s.rotationVector(i * 0.1, 3.2, rnd)
            val normSq = q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]
            assertEquals(1.0, normSq.toDouble(), 0.05)
        }
    }
}
