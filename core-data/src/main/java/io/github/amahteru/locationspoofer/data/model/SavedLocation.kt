package io.github.amahteru.locationspoofer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedLocation(
    val name: String,
    val lat: Double,
    val lng: Double
)
