package io.github.amahteru.locationspoofer.ui.screen.tabs.route

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.data.model.RoutePoint
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import kotlin.math.cos

val RouteCameraLeft = 36.dp
val RouteCameraTop = 80.dp
val RouteCameraRight = 96.dp

fun AppMapController.fitRoute(points: List<RoutePoint>, density: Density, bottomActionHeight: Dp, bottomBarHeight: Dp) {
    val bottom = if (bottomActionHeight > 0.dp) bottomActionHeight + bottomBarHeight + 24.dp else bottomBarHeight + 160.dp
    val centerLat = (points.minOf { it.lat } + points.maxOf { it.lat }) / 2
    val centerLng = (points.minOf { it.lng } + points.maxOf { it.lng }) / 2
    val halfLat = MIN_ROUTE_SPAN_M / 2 / METERS_PER_DEGREE
    val halfLng = halfLat / cos(Math.toRadians(centerLat))
    val area = points.map { it.lat to it.lng } +
        listOf(centerLat - halfLat to centerLng - halfLng, centerLat + halfLat to centerLng + halfLng)
    with(density) {
        fitBounds(area, RouteCameraLeft.roundToPx(), RouteCameraTop.roundToPx(), RouteCameraRight.roundToPx(), bottom.roundToPx())
    }
}

private const val MIN_ROUTE_SPAN_M = 300.0
private const val METERS_PER_DEGREE = 111_320.0
