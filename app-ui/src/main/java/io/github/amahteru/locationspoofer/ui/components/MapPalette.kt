package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.theme.onSuccess
import io.github.amahteru.locationspoofer.ui.theme.onWarning
import io.github.amahteru.locationspoofer.ui.theme.success
import io.github.amahteru.locationspoofer.ui.theme.warning

const val MAP_LINE_WIDTH_DP = 4f

class MapPalette(
    val route: Int,
    val routeArea: Int,
    val coverageFill: Int,
    val coverageStroke: Int,
    val start: MapMarkerStyle,
    val end: MapMarkerStyle,
    val current: MapMarkerStyle,
    private val waypoint: MapMarkerStyle
) {
    fun waypoint(number: Int) = waypoint.copy(label = "$number")
}

@Composable
fun rememberMapPalette(): MapPalette {
    val scheme = MaterialTheme.colorScheme
    val startLabel = stringResource(R.string.route_start_badge)
    val endLabel = stringResource(R.string.route_end_badge)
    val currentLabel = stringResource(R.string.route_current_badge)
    return remember(scheme, startLabel, endLabel, currentLabel) {
        MapPalette(
            route = scheme.primary.toArgb(),
            routeArea = scheme.primary.copy(alpha = 0.12f).toArgb(),
            coverageFill = scheme.success.copy(alpha = 0.2f).toArgb(),
            coverageStroke = scheme.success.copy(alpha = 0.4f).toArgb(),
            start = MapMarkerStyle(startLabel, scheme.success.toArgb(), scheme.onSuccess.toArgb()),
            end = MapMarkerStyle(endLabel, scheme.error.toArgb(), scheme.onError.toArgb()),
            current = MapMarkerStyle(currentLabel, scheme.warning.toArgb(), scheme.onWarning.toArgb(), zIndex = 2),
            waypoint = MapMarkerStyle("", scheme.tertiary.toArgb(), scheme.onTertiary.toArgb())
        )
    }
}
