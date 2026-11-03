package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.amahteru.locationspoofer.data.model.AppMapType
import io.github.amahteru.locationspoofer.data.model.MapEngine
import io.github.amahteru.locationspoofer.ui.components.map.AMapViewContainer
import io.github.amahteru.locationspoofer.ui.components.map.BaiduMapViewContainer
import io.github.amahteru.locationspoofer.ui.components.map.GoogleMapViewContainer
import io.github.amahteru.locationspoofer.ui.theme.AppMotion

interface AppMapOverlay {
    fun remove()
}

interface AppMapMarker : AppMapOverlay {
    fun setPosition(lat: Double, lng: Double)
    fun setStyle(style: MapMarkerStyle)
}

data class MapMarkerStyle(val label: String, val color: Int, val onColor: Int, val zIndex: Int = 1)

data class MapCamera(val lat: Double, val lng: Double, val zoom: Float)

const val STREET_ZOOM = 17f

interface AppMapController {
    fun clear()
    fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, widthDp: Float): AppMapOverlay
    fun addCircle(
        lat: Double,
        lng: Double,
        radius: Double,
        fillColorInt: Int,
        strokeColorInt: Int,
        strokeWidthDp: Float
    )

    fun addMarker(lat: Double, lng: Double, style: MapMarkerStyle, animate: Boolean = false): AppMapMarker
    fun animateCamera(lat: Double, lng: Double, zoom: Float? = null)
    fun fitBounds(points: List<Pair<Double, Double>>, padding: Int) {
        fitBounds(points, padding, padding, padding, padding)
    }

    fun fitBounds(
        points: List<Pair<Double, Double>>,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    )

    fun moveCamera(lat: Double, lng: Double, zoom: Float? = null)
    fun screenLocation(lat: Double, lng: Double): android.graphics.Point?
    val cameraTargetLat: Double?
    val cameraTargetLng: Double?
    val cameraZoom: Float?
    fun focusOn(lat: Double, lng: Double) {
        animateCamera(lat, lng, maxOf(cameraZoom ?: 0f, STREET_ZOOM))
    }
    fun setOnCameraChangeListener(onFinish: (lat: Double, lng: Double) -> Unit)
    fun setOnCameraMoveListener(onMove: (lat: Double, lng: Double) -> Unit)
    fun disableUiControls()
    fun setMapType(type: AppMapType)
    fun setDarkMode(isDark: Boolean, context: android.content.Context)
}

@Composable
fun AppMapView(
    mapEngine: MapEngine,
    modifier: Modifier = Modifier,
    initialCamera: MapCamera? = null,
    onMapReady: (AppMapController) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = isSystemInDarkTheme()
    var mapController by remember { mutableStateOf<AppMapController?>(null) }

    LaunchedEffect(isDark, mapController) {
        mapController?.setDarkMode(isDark, context)
    }

    val activeEngine = if (mapEngine == MapEngine.AUTO) {
        MapEngine.AMAP
    } else {
        mapEngine
    }
    var settledEngine by remember { mutableStateOf(activeEngine) }
    var renderedEngine by remember { mutableStateOf<MapEngine?>(null) }

    Box(modifier = modifier) {
        listOf(settledEngine, activeEngine).distinct().forEach { engine ->
            key(engine) {
                val alpha = animateFloatAsState(
                    targetValue = if (engine == renderedEngine || engine != activeEngine) 1f else 0f,
                    animationSpec = AppMotion.fadeThroughInSpec(),
                    label = "map_reveal",
                    finishedListener = { if (it == 1f && engine == activeEngine) settledEngine = engine }
                )
                val engineModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha.value }
                val onEngineReady = { controller: AppMapController ->
                    mapController = controller
                    renderedEngine = engine
                    onMapReady(controller)
                }

                when (engine) {
                    MapEngine.BAIDU -> BaiduMapViewContainer(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        isDark = isDark,
                        initialCamera = initialCamera,
                        modifier = engineModifier,
                        onMapReady = onEngineReady
                    )

                    MapEngine.GOOGLE -> GoogleMapViewContainer(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        isDark = isDark,
                        initialCamera = initialCamera,
                        modifier = engineModifier,
                        onMapReady = onEngineReady
                    )

                    else -> AMapViewContainer(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        isDark = isDark,
                        initialCamera = initialCamera,
                        modifier = engineModifier,
                        onMapReady = onEngineReady
                    )
                }
            }
        }
    }
}
