package io.github.amahteru.locationspoofer.data.motion

import io.github.amahteru.locationspoofer.data.model.RoutePoint
import io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng
import io.github.amahteru.locationspoofer.utils.GeoMath
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionControllerTest {

    private class FakeSink : MotionController.MotionSink {
        data class Manual(val lat: Double, val lng: Double, val speed: Double)
        data class Route(val offset: Double, val start: Long, val preset: MotionController.SpeedPreset)

        val manual: MutableList<Manual> = Collections.synchronizedList(ArrayList())
        val routes: MutableList<Route> = Collections.synchronizedList(ArrayList())
        var saved: Pair<String, List<RoutePoint>>? = null

        override suspend fun writeManual(lat: Double, lng: Double, bearing: Float, speedMs: Double, timestamp: Long) {
            manual += Manual(lat, lng, speedMs)
        }

        override suspend fun writeRoute(
            lat: Double, lng: Double, bearing: Float, preset: MotionController.SpeedPreset,
            points: List<RoutePoint>, startTimestamp: Long, distanceOffset: Double, stopAtDestination: Boolean
        ) {
            routes += Route(distanceOffset, startTimestamp, preset)
        }

        override suspend fun saveRoute(name: String, points: List<RoutePoint>) {
            saved = name to points
        }
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not met in time", condition())
    }

    private var now = 1_000_000L
    private val sink = FakeSink()
    private val walking = MotionController.SpeedPreset("WALKING", 2.0)
    private val controller = MotionController(sink, realismParams = { 0 to 0 }, clock = { now }, autoTick = false)

    private val start = LatLng(30.0, 120.0)
    private val end = GeoMath.destination(start, 0.0, 1000.0)

    private fun here() = LatLng(controller.state.value.lat, controller.state.value.lng)

    private fun startRoute() {
        controller.onRouteStarted(
            listOf(RoutePoint(start.lat, start.lng), RoutePoint(end.lat, end.lng)),
            walking, stopAtDestination = false, startTimestamp = now
        )
        controller.tick(now)
    }

    private fun advance(ms: Long) {
        now += ms
        controller.tick(now)
    }

    @Test
    fun `route playback advances along the path`() {
        startRoute()
        advance(10_000)
        assertEquals(20.0, GeoMath.distance(start, here()), 0.1)
        assertEquals(MotionController.Mode.ROUTE, controller.state.value.mode)
    }

    @Test
    fun `joystick is ignored while the route is playing`() {
        startRoute()
        controller.joystick(90.0, 1f)
        advance(5_000)
        assertEquals(10.0, GeoMath.distance(start, here()), 0.1)
    }

    @Test
    fun `pause hands control to the joystick and freezes the route`() {
        startRoute()
        advance(10_000)
        controller.pause()
        val paused = here()
        assertTrue(controller.state.value.paused)
        awaitUntil { sink.manual.isNotEmpty() }
        assertEquals(0.0, sink.manual.last().speed, 0.0)

        advance(5_000)
        assertEquals(0.0, GeoMath.distance(paused, here()), 1e-6)

        controller.joystick(90.0, 1f)
        advance(1_000)
        assertEquals(2.0, GeoMath.distance(paused, here()), 0.05)
        controller.joystick(0.0, 0f)
        awaitUntil { sink.manual.last().speed == 0.0 }
    }

    @Test
    fun `resume walks back to the pause point and continues from the paused distance`() {
        startRoute()
        advance(10_000)
        controller.pause()
        val pausePoint = here()
        controller.joystick(90.0, 1f)
        repeat(5) { advance(1_000) }
        controller.joystick(0.0, 0f)
        assertEquals(10.0, GeoMath.distance(pausePoint, here()), 0.1)

        controller.resume()
        assertTrue(controller.state.value.returning)
        var last = GeoMath.distance(pausePoint, here())
        repeat(20) {
            if (!controller.state.value.returning) return@repeat
            advance(500)
            val d = GeoMath.distance(pausePoint, here())
            assertTrue(d <= last + 1e-6)
            assertTrue(last - d <= 1.0 + 1e-6)
            last = d
        }
        assertFalse(controller.state.value.returning)
        assertEquals(MotionController.Mode.ROUTE, controller.state.value.mode)
        awaitUntil { sink.routes.isNotEmpty() }
        assertEquals(20.0, sink.routes.last().offset, 0.01)

        advance(5_000)
        assertEquals(30.0, GeoMath.distance(start, here()), 0.1)
    }

    @Test
    fun `changing speed during a route does not jump the position`() {
        startRoute()
        advance(10_000)
        val before = here()
        controller.setSpeedPreset(MotionController.SpeedPreset("RUNNING", 4.0))
        controller.tick(now)
        assertEquals(0.0, GeoMath.distance(before, here()), 0.01)
        advance(5_000)
        assertEquals(40.0, GeoMath.distance(start, here()), 0.1)
    }

    @Test
    fun `recording samples the joystick path and saves it as a route`() = runBlocking {
        controller.onStaticStarted(start.lat, start.lng)
        controller.setSpeedPreset(walking)
        controller.tick(now)
        controller.startRecording()
        controller.joystick(90.0, 1f)
        repeat(15) { advance(1_000) }
        controller.joystick(0.0, 1f)
        repeat(5) { advance(1_000) }
        controller.joystick(0.0, 0f)
        controller.stopRecording()

        val state = controller.state.value
        assertTrue(state.hasPendingRecording)
        assertEquals(40.0, state.recordedDistanceM, 1.0)

        assertTrue(controller.saveRecording("测试路线"))
        val (name, points) = sink.saved!!
        assertEquals("测试路线", name)
        assertEquals(start.lat, points.first().lat, 1e-9)
        assertTrue(points.size in 7..12)
        assertFalse(controller.state.value.hasPendingRecording)
    }

    @Test
    fun `manual route starts paused and Start walks to the route before following it`() {
        controller.onRouteStarted(
            listOf(RoutePoint(start.lat, start.lng), RoutePoint(end.lat, end.lng)),
            walking, stopAtDestination = false, startTimestamp = now, startPaused = true
        )
        controller.tick(now)
        assertTrue(controller.state.value.paused)
        assertTrue(controller.state.value.hasRoute)
        assertEquals(MotionController.Mode.MANUAL, controller.state.value.mode)

        controller.joystick(90.0, 1f)
        repeat(3) { advance(1_000) }
        controller.joystick(0.0, 0f)

        controller.resume()
        assertTrue(controller.state.value.returning)
        repeat(10) { if (controller.state.value.returning) advance(1_000) }
        assertEquals(MotionController.Mode.ROUTE, controller.state.value.mode)
        awaitUntil { sink.routes.isNotEmpty() }
        assertEquals(0.0, sink.routes.last().offset, 1e-9)

        advance(5_000)
        assertEquals(10.0, GeoMath.distance(start, here()), 0.1)
    }

    @Test
    fun `stopping clears the session`() {
        startRoute()
        controller.onStopped()
        assertFalse(controller.state.value.active)
        controller.joystick(90.0, 1f)
        advance(1_000)
        assertFalse(controller.state.value.active)
    }
}
