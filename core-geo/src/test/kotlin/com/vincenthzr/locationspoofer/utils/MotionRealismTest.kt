package com.vincenthzr.locationspoofer.utils

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
    }

    @Test
    fun `sessions with the same start timestamp agree across processes`() {
        val a = MotionRealism.Session(start, MotionRealism.Level.HIGH, 20)
        val b = MotionRealism.Session(start, MotionRealism.Level.HIGH, 20)
        val c = MotionRealism.Session(start + 1, MotionRealism.Level.HIGH, 20)
        assertEquals(a.distance(3.0, 777.0), b.distance(3.0, 777.0), 0.0)
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
    fun `session cache returns the same instance for the same parameters`() {
        val a = MotionRealism.session(start, 2, 10)
        val b = MotionRealism.session(start, 2, 10)
        val c = MotionRealism.session(start, 2, 20)
        assertTrue(a === b)
        assertTrue(a !== c)
    }
}
