package com.vincenthzr.locationspoofer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoordinateUtilsTest {

    private val beijingLat = 39.9087
    private val beijingLng = 116.3975

    @Test
    fun `wgs84ToGcj02 leaves out-of-China coordinates unchanged`() {
        val nyLat = 40.7128
        val nyLng = -74.0060
        val result = CoordinateUtils.wgs84ToGcj02(nyLat, nyLng)
        assertEquals(nyLat, result.lat, 1e-12)
        assertEquals(nyLng, result.lng, 1e-12)
    }

    @Test
    fun `gcj02ToWgs84 leaves out-of-China coordinates unchanged`() {
        val nyLat = 40.7128
        val nyLng = -74.0060
        val result = CoordinateUtils.gcj02ToWgs84(nyLat, nyLng)
        assertEquals(nyLat, result.lat, 1e-12)
        assertEquals(nyLng, result.lng, 1e-12)
    }

    @Test
    fun `wgs84ToGcj02 shifts in-China coordinates by a non-trivial but bounded amount`() {
        val result = CoordinateUtils.wgs84ToGcj02(beijingLat, beijingLng)
        val latDelta = abs(result.lat - beijingLat)
        val lngDelta = abs(result.lng - beijingLng)
        assertTrue("expected a non-zero offset, got $latDelta", latDelta > 1e-6)
        assertTrue("offset too large: $latDelta", latDelta < 0.01)
        assertTrue("expected a non-zero offset, got $lngDelta", lngDelta > 1e-6)
        assertTrue("offset too large: $lngDelta", lngDelta < 0.01)
    }

    @Test
    fun `wgs84 to gcj02 and back round-trips within half a millimeter`() {
        val gcj = CoordinateUtils.wgs84ToGcj02(beijingLat, beijingLng)
        val backToWgs = CoordinateUtils.gcj02ToWgs84(gcj.lat, gcj.lng)
        assertEquals(beijingLat, backToWgs.lat, 5e-8)
        assertEquals(beijingLng, backToWgs.lng, 5e-8)
    }

    @Test
    fun `gcj02 to bd09 and back round-trips accurately`() {
        val bd = CoordinateUtils.gcj02ToBd09(beijingLat, beijingLng)
        val backToGcj = CoordinateUtils.bd09ToGcj02(bd.lat, bd.lng)
        assertEquals(beijingLat, backToGcj.lat, 1e-6)
        assertEquals(beijingLng, backToGcj.lng, 1e-6)
    }

    @Test
    fun `gcj02ToBd09 shifts coordinates by a non-trivial but bounded amount`() {
        val result = CoordinateUtils.gcj02ToBd09(beijingLat, beijingLng)
        val latDelta = abs(result.lat - beijingLat)
        val lngDelta = abs(result.lng - beijingLng)
        assertTrue(latDelta > 1e-6)
        assertTrue(latDelta < 0.01)
        assertTrue(lngDelta > 1e-6)
        assertTrue(lngDelta < 0.01)
    }
}
