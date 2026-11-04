package io.github.amahteru.locationspoofer.data.model

import kotlin.math.abs
import kotlinx.serialization.Serializable

private const val SAME_COORDINATE_DEGREES = 0.00001

@Serializable
data class SavedLocation(
    val name: String,
    val lat: Double,
    val lng: Double
)

fun SavedLocation.isAt(lat: Double, lng: Double): Boolean =
    abs(this.lat - lat) < SAME_COORDINATE_DEGREES && abs(this.lng - lng) < SAME_COORDINATE_DEGREES
