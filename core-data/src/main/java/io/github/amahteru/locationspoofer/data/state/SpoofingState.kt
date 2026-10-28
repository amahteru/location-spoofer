package io.github.amahteru.locationspoofer.data.state

object SpoofingState {
    var isActive = false
    var latitude = 0.0
    var longitude = 0.0
    var wifiJson = "[]"
    var cellJson = "[]"
    var bluetoothJson = "[]"
    var simMode = "STILL"
    var simBearing = 0f
    var startTimestamp = 0L
    var routeJson = "[]"
    var isRouteMode = false
    var enableJitter = true

    var realismLevel = -1
    var speedFluctuationPct = -1

    var routeDistanceOffset = 0.0
}
