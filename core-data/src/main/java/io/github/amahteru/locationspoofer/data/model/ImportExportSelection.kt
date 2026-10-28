package io.github.amahteru.locationspoofer.data.model

data class ImportExportSelection(
    val locations: Boolean = true,
    val savedLocations: Boolean = true,
    val savedRoutes: Boolean = true,
    val settings: Boolean = true,
    val apiKeys: Boolean = false
) {
    val hasAny: Boolean
        get() = locations || savedLocations || savedRoutes ||
                settings || apiKeys
}

data class ImportExportCounts(
    val locations: Int = 0,
    val savedLocations: Int = 0,
    val savedRoutes: Int = 0,
    val settings: Int = 0,
    val apiKeys: Int = 0
)
