package io.github.amahteru.locationspoofer.data.model

import io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord
import io.github.amahteru.locationspoofer.data.db.CompleteCollectionRoute
import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.data.db.LocationConnectedWifi
import io.github.amahteru.locationspoofer.data.db.LocationRecord
import io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng
import io.github.amahteru.locationspoofer.utils.GeoMath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentResolverTest {
    private val center = LatLng(30.0, 104.0)

    private fun point(id: Long, distance: Double, routeId: Long? = null): CompleteLocation {
        val p = GeoMath.destination(center, 0.0, distance)
        return CompleteLocation(LocationRecord(id = id, lat = p.lat, lng = p.lng, collectionRouteId = routeId))
    }

    private fun recordsAtCenter(
        locations: List<CompleteLocation>,
        routes: List<CompleteCollectionRoute> = emptyList()
    ) = EnvironmentResolver.recordsAt(locations, routes, center.lat, center.lng)

    @Test fun keepsPointsWithin50mSortedByDistance() {
        val records = recordsAtCenter(listOf(point(1, 40.0), point(2, 10.0), point(3, 60.0)))
        assertEquals(listOf(2L, 1L), records.map { it.location.id })
    }

    @Test fun ignoresRouteSamplesOutsideMatchedRoute() {
        assertTrue(recordsAtCenter(listOf(point(1, 10.0, routeId = 9))).isEmpty())
    }

    @Test fun matchedRouteUsesItsOwnSamples() {
        val end = GeoMath.destination(center, 0.0, 1000.0)
        val route = CompleteCollectionRoute(
            CollectionRouteRecord(
                id = 1,
                name = "Route",
                pointsJson = Json.encodeToString(listOf(RoutePoint(center.lat, center.lng), RoutePoint(end.lat, end.lng)))
            ),
            listOf(point(5, 0.0, routeId = 1))
        )
        assertEquals(listOf(5L), recordsAtCenter(listOf(point(1, 10.0)), listOf(route)).map { it.location.id })
    }

    @Test fun connectedWifiCounts() {
        val connected = point(1, 0.0).copy(connectedWifi = LocationConnectedWifi(locationId = 1, bssid = "AA:BB:CC:00:00:01"))
        assertTrue(EnvironmentResolver.hasWifi(listOf(connected)))
        assertFalse(EnvironmentResolver.hasWifi(emptyList()))
    }

    @Test fun recordsWithoutSignalsProduceEmptyEnvironment() {
        val (wifi, cell, bluetooth) = EnvironmentResolver.toJson(listOf(point(1, 0.0)), center.lat, center.lng)
        assertFalse(JSONObject(wifi).getBoolean("isConnected"))
        assertEquals(0, JSONObject(wifi).getJSONArray("nearbyWifi").length())
        assertEquals(0, JSONArray(cell).length())
        assertEquals(0, JSONArray(bluetooth).length())
    }
}
