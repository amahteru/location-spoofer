package io.github.amahteru.locationspoofer.ui.components.map

import android.content.Context
import android.graphics.Point
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import com.baidu.mapapi.animation.AlphaAnimation
import com.baidu.mapapi.animation.Animation
import com.baidu.mapapi.animation.AnimationSet
import com.baidu.mapapi.animation.ScaleAnimation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BaiduMapOptions
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.Stroke
import com.baidu.mapapi.map.TextureMapView
import com.baidu.mapapi.model.LatLng
import io.github.amahteru.locationspoofer.data.model.AppMapType
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.AppMapMarker
import io.github.amahteru.locationspoofer.ui.components.AppMapOverlay
import io.github.amahteru.locationspoofer.ui.components.MapCamera
import io.github.amahteru.locationspoofer.ui.components.MapMarkerStyle

class BaiduMapControllerImpl(
    private val map: BaiduMap,
    private val mapView: TextureMapView,
    private val context: Context
) : AppMapController {
    private var currentMapType: AppMapType = AppMapType.NORMAL

    override fun setDarkMode(isDark: Boolean, context: Context) {
        val customStyleOptions = com.baidu.mapapi.map.MapCustomStyleOptions()
        val darkStyleId = darkStyleId(context)
        if (isDark && darkStyleId.isNotEmpty()) {
            customStyleOptions.customStyleId(darkStyleId)
            mapView.setMapCustomStyle(
                customStyleOptions,
                object : com.baidu.mapapi.map.CustomMapStyleCallBack {
                    override fun onPreLoadLastCustomMapStyle(p0: String?): Boolean = false
                    override fun onCustomMapStyleLoadSuccess(p0: Boolean, p1: String?): Boolean =
                        true

                    override fun onCustomMapStyleLoadFailed(
                        p0: Int,
                        p1: String?,
                        p2: String?
                    ): Boolean = false
                })
            mapView.setMapCustomStyleEnable(true)
        } else {
            mapView.setMapCustomStyleEnable(false)
        }
        setMapType(currentMapType)
    }

    private fun darkStyleId(context: Context): String = try {
        context.packageManager
            .getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.ApplicationInfoFlags.of(
                    android.content.pm.PackageManager.GET_META_DATA.toLong()
                )
            ).metaData?.getString("io.github.amahteru.locationspoofer.BAIDU_DARK_STYLE_ID").orEmpty().trim()
    } catch (e: Exception) {
        ""
    }

    override fun clear() {
        map.clear()
    }

    private val density = context.resources.displayMetrics.density

    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, widthDp: Float): AppMapOverlay {
        if (points.size < 2) return object : AppMapOverlay {
            override fun remove() {}
        }
        val polyline = map.addOverlay(
            com.baidu.mapapi.map.PolylineOptions()
                .width(widthDp * density / TEXTURED_LINE_SCALE)
                .lineCapType(com.baidu.mapapi.map.PolylineOptions.LineCapType.LineCapRound)
                .lineJoinType(com.baidu.mapapi.map.PolylineOptions.LineJoinType.LineJoinRound)
                .customTexture(com.baidu.mapapi.map.BitmapDescriptorFactory.fromBitmap(MapMarkerIcons.line(colorInt)))
                .points(points.map { LatLng(it.first, it.second) })
        )
        return object : AppMapOverlay {
            override fun remove() {
                polyline?.remove()
            }
        }
    }

    override fun addCircle(
        lat: Double,
        lng: Double,
        radius: Double,
        fillColorInt: Int,
        strokeColorInt: Int,
        strokeWidthDp: Float
    ) {
        map.addOverlay(
            com.baidu.mapapi.map.CircleOptions()
                .center(LatLng(lat, lng))
                .radius(radius.toInt())
                .fillColor(fillColorInt)
                .stroke(Stroke(strokeWidthDp * density, strokeColorInt))
        )
    }

    override fun addMarker(lat: Double, lng: Double, style: MapMarkerStyle, animate: Boolean): AppMapMarker {
        val marker = map.addOverlay(
            com.baidu.mapapi.map.MarkerOptions()
                .position(LatLng(lat, lng))
                .icon(icon(style))
                .anchor(0.5f, MapMarkerIcons.ANCHOR_Y)
                .zIndex(style.zIndex)
        ) as Marker
        if (animate) marker.play(MarkerTransition.Enter)
        return object : AppMapMarker {
            var current = style

            override fun setPosition(lat: Double, lng: Double) {
                marker.position = LatLng(lat, lng)
            }

            override fun setStyle(style: MapMarkerStyle) {
                if (style == current) return
                current = style
                marker.setIcon(icon(style))
            }

            override fun remove() {
                marker.play(MarkerTransition.Exit) { marker.remove() }
            }
        }
    }

    private fun icon(style: MapMarkerStyle) =
        com.baidu.mapapi.map.BitmapDescriptorFactory.fromBitmap(MapMarkerIcons.marker(context, style))

    private fun Marker.play(transition: MarkerTransition, onEnd: () -> Unit = {}) {
        setAnimation(AnimationSet().apply {
            listOf(ScaleAnimation(transition.scaleFrom, transition.scaleTo), AlphaAnimation(transition.alphaFrom, transition.alphaTo)).forEach {
                it.setDuration(transition.durationMs)
                it.setInterpolator(transition.interpolator())
                addAnimation(it)
            }
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart() {}
                override fun onAnimationEnd() = onEnd()
                override fun onAnimationCancel() {}
                override fun onAnimationRepeat() {}
            })
        })
        startAnimation()
    }

    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        val update = if (zoom != null) com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom(
            LatLng(lat, lng), zoom
        )
        else com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(
            LatLng(lat, lng)
        )
        map.animateMapStatus(update)
    }

    override fun fitBounds(
        points: List<Pair<Double, Double>>,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    ) {
        val projection = map.projection ?: return
        if (points.isEmpty() || mapView.width == 0) return
        val northEast = projection.toScreenLocation(LatLng(points.maxOf { it.first }, points.maxOf { it.second }))
        val southWest = projection.toScreenLocation(LatLng(points.minOf { it.first }, points.minOf { it.second }))
        val width = mapView.width - paddingLeft - paddingRight
        val height = mapView.height - paddingTop - paddingBottom
        val fit = minOf(width / maxOf(1f, abs(northEast.x - southWest.x).toFloat()), height / maxOf(1f, abs(northEast.y - southWest.y).toFloat()))
        val zoom = (map.mapStatus.zoom + log2(fit)).coerceAtMost(map.maxZoomLevel)
        val scale = 2f.pow(map.mapStatus.zoom - zoom)
        val offsetX = mapView.width / 2f - (paddingLeft + width / 2f)
        val offsetY = mapView.height / 2f - (paddingTop + height / 2f)
        val target = projection.fromScreenLocation(
            Point(
                ((northEast.x + southWest.x) / 2f + offsetX * scale).toInt(),
                ((northEast.y + southWest.y) / 2f + offsetY * scale).toInt()
            )
        )
        map.animateMapStatus(
            com.baidu.mapapi.map.MapStatusUpdateFactory.newMapStatus(MapStatus.Builder().target(target).zoom(zoom).build())
        )
    }

    override fun screenLocation(lat: Double, lng: Double): Point? = map.projection?.toScreenLocation(LatLng(lat, lng))

    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        val update = if (zoom != null) com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom(
            LatLng(lat, lng), zoom
        )
        else com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(
            LatLng(lat, lng)
        )
        map.setMapStatus(update)
    }

    override val cameraTargetLat: Double?
        get() = map.mapStatus?.target?.latitude
    override val cameraTargetLng: Double?
        get() = map.mapStatus?.target?.longitude
    override val cameraZoom: Float?
        get() = map.mapStatus?.zoom

    private var cameraFinishListener: ((Double, Double) -> Unit)? = null
    private var cameraMoveListener: ((Double, Double) -> Unit)? = null

    init {
        map.setOnMapStatusChangeListener(object :
            BaiduMap.OnMapStatusChangeListener {
            private var lastReason: Int = 0
            override fun onMapStatusChangeStart(p0: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeStart(p0: com.baidu.mapapi.map.MapStatus?, p1: Int) {
                lastReason = p1
            }

            override fun onMapStatusChange(p0: com.baidu.mapapi.map.MapStatus?) {
                if (lastReason == BaiduMap.OnMapStatusChangeListener.REASON_GESTURE) {
                    p0?.target?.let {
                        cameraMoveListener?.invoke(it.latitude, it.longitude)
                    }
                }
            }

            override fun onMapStatusChangeFinish(p0: com.baidu.mapapi.map.MapStatus?) {
                p0?.target?.let {
                    cameraFinishListener?.invoke(it.latitude, it.longitude)
                }
            }
        })
    }

    override fun setOnCameraChangeListener(onFinish: (lat: Double, lng: Double) -> Unit) {
        cameraFinishListener = onFinish
    }

    override fun setOnCameraMoveListener(onMove: (lat: Double, lng: Double) -> Unit) {
        cameraMoveListener = onMove
    }

    override fun disableUiControls() {
        map.uiSettings.isZoomGesturesEnabled = true
        map.uiSettings.isScrollGesturesEnabled = true
        map.uiSettings.isOverlookingGesturesEnabled = true
        map.uiSettings.isRotateGesturesEnabled = false
    }

    override fun setMapType(type: AppMapType) {
        currentMapType = type
        map.mapType = if (type == AppMapType.SATELLITE) BaiduMap.MAP_TYPE_SATELLITE else BaiduMap.MAP_TYPE_NORMAL
        val overlook = if (type == AppMapType.MAP_3D) -45f else 0f
        val status = map.mapStatus ?: return
        if (status.overlook != overlook) {
            map.setMapStatus(
                com.baidu.mapapi.map.MapStatusUpdateFactory.newMapStatus(
                    com.baidu.mapapi.map.MapStatus.Builder(status).overlook(overlook).build()
                )
            )
        }
    }
}

@Composable
fun BaiduMapViewContainer(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    isDark: Boolean,
    initialCamera: MapCamera?,
    modifier: Modifier,
    onMapReady: (AppMapController) -> Unit
) {
    val baiduMapView = remember {
        TextureMapView(
            context,
            BaiduMapOptions().apply {
                initialCamera?.let { mapStatus(MapStatus.Builder().target(LatLng(it.lat, it.lng)).zoom(it.zoom).build()) }
            }
        )
    }

    DisposableEffect(lifecycleOwner.lifecycle, baiduMapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> baiduMapView.onResume()
                Lifecycle.Event.ON_PAUSE -> baiduMapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            baiduMapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            baiduMapView.onPause()
            baiduMapView.onDestroy()
        }
    }

    MapAndroidView(baiduMapView, modifier) {
        showZoomControls(false)
        val controller = BaiduMapControllerImpl(map, this, context)
        controller.setDarkMode(isDark, context)
        map.setOnFirstFrameDrawListener { post { onMapReady(controller) } }
    }
}

private const val TEXTURED_LINE_SCALE = 1.5f
