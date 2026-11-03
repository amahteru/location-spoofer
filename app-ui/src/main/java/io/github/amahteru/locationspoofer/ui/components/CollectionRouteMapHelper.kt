package io.github.amahteru.locationspoofer.ui.components

import android.graphics.Color
import io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord
import io.github.amahteru.locationspoofer.data.model.geometry
import io.github.amahteru.locationspoofer.utils.GeoMath

object CollectionRouteMapHelper {
    fun draw(controller: AppMapController, route: CollectionRouteRecord, palette: MapPalette, maxCircles: Int = 80) {
        val points = route.geometry()
        if (points.size < 2) return
        controller.addPolyline(points.map { it.lat to it.lng }, palette.route, MAP_LINE_WIDTH_DP)
        val lengths = points.zipWithNext().map { (a, b) -> GeoMath.distance(a, b) }
        val total = lengths.sum()
        val step = maxOf(25.0, total / maxOf(1, maxCircles - 1))
        var distance = 0.0
        var segmentStart = 0.0
        var index = 0
        var circles = 0
        while (distance <= total && circles < maxCircles) {
            while (index < lengths.lastIndex && distance > segmentStart + lengths[index]) {
                segmentStart += lengths[index++]
            }
            val point = GeoMath.destination(points[index], GeoMath.bearing(points[index], points[index + 1]).toDouble(), distance - segmentStart)
            controller.addCircle(point.lat, point.lng, route.halfWidthM, palette.routeArea, Color.TRANSPARENT, 0f)
            distance += step
            circles++
        }
        val last = points.last()
        controller.addCircle(last.lat, last.lng, route.halfWidthM, palette.routeArea, Color.TRANSPARENT, 0f)
        controller.addMarker(points.first().lat, points.first().lng, palette.start)
        controller.addMarker(last.lat, last.lng, palette.end)
    }

    fun show(controller: AppMapController, route: CollectionRouteRecord, palette: MapPalette, bottomPadding: Int = 360) {
        controller.clear()
        draw(controller, route, palette)
        controller.fitBounds(route.geometry().map { it.lat to it.lng }, 80, 120, 80, bottomPadding)
    }
}
