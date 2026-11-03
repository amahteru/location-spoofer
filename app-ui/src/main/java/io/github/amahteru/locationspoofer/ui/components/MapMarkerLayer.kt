package io.github.amahteru.locationspoofer.ui.components

data class MapMarkerSpec(val lat: Double, val lng: Double, val style: MapMarkerStyle)

class MapMarkerLayer {
    private val markers = mutableMapOf<Any, AppMapMarker>()

    fun update(map: AppMapController, specs: Map<Any, MapMarkerSpec>) {
        (markers.keys - specs.keys).forEach { markers.remove(it)?.remove() }
        specs.forEach { (key, spec) ->
            markers[key]?.run {
                setPosition(spec.lat, spec.lng)
                setStyle(spec.style)
            } ?: run { markers[key] = map.addMarker(spec.lat, spec.lng, spec.style, animate = true) }
        }
    }

    fun reset() = markers.clear()
}
