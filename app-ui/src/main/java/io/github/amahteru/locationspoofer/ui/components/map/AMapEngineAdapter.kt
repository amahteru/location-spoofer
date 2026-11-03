package io.github.amahteru.locationspoofer.ui.components.map

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapOptions
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng as AMapLatLng
import com.amap.api.maps.model.MarkerOptions as AMapMarkerOptions
import com.amap.api.maps.model.PolylineOptions as AMapPolylineOptions
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.animation.AlphaAnimation
import com.amap.api.maps.model.animation.Animation
import com.amap.api.maps.model.animation.AnimationSet
import com.amap.api.maps.model.animation.ScaleAnimation
import io.github.amahteru.locationspoofer.data.model.AppMapType
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.AppMapMarker
import io.github.amahteru.locationspoofer.ui.components.AppMapOverlay
import io.github.amahteru.locationspoofer.ui.components.MapCamera
import io.github.amahteru.locationspoofer.ui.components.MapMarkerStyle

class AMapControllerImpl(
    private val map: AMap,
    private val context: Context
) : AppMapController {
    private var isDarkMode: Boolean = false
    private var currentMapType: AppMapType = AppMapType.NORMAL

    override fun setDarkMode(isDark: Boolean, context: Context) {
        isDarkMode = isDark
        setMapType(currentMapType)
    }

    override fun clear() {
        map.clear()
    }

    private val density = context.resources.displayMetrics.density

    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, widthDp: Float): AppMapOverlay {
        val polyline = map.addPolyline(
            AMapPolylineOptions()
                .width(widthDp * density * MapMarkerIcons.lineTextureScale)
                .setUseTexture(true)
                .lineCapType(AMapPolylineOptions.LineCapType.LineCapRound)
                .lineJoinType(AMapPolylineOptions.LineJoinType.LineJoinRound)
                .setCustomTexture(BitmapDescriptorFactory.fromBitmap(MapMarkerIcons.line(colorInt)))
                .apply { points.forEach { add(AMapLatLng(it.first, it.second)) } }
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
        map.addCircle(
            com.amap.api.maps.model.CircleOptions()
                .center(AMapLatLng(lat, lng))
                .radius(radius)
                .fillColor(fillColorInt)
                .strokeColor(strokeColorInt)
                .strokeWidth(strokeWidthDp * density)
        )
    }

    override fun addMarker(lat: Double, lng: Double, style: MapMarkerStyle, animate: Boolean): AppMapMarker {
        val marker = map.addMarker(
            AMapMarkerOptions()
                .position(AMapLatLng(lat, lng))
                .icon(BitmapDescriptorFactory.fromBitmap(MapMarkerIcons.marker(context, style)))
                .anchor(0.5f, MapMarkerIcons.ANCHOR_Y)
                .zIndex(style.zIndex.toFloat())
        )
        if (animate) marker.play(MarkerTransition.Enter)
        return object : AppMapMarker {
            var current = style

            override fun setPosition(lat: Double, lng: Double) {
                marker.position = AMapLatLng(lat, lng)
            }

            override fun setStyle(style: MapMarkerStyle) {
                if (style == current) return
                current = style
                marker.setIcon(BitmapDescriptorFactory.fromBitmap(MapMarkerIcons.marker(context, style)))
            }

            override fun remove() {
                marker.play(MarkerTransition.Exit) { marker.remove() }
            }
        }
    }

    private fun Marker.play(transition: MarkerTransition, onEnd: () -> Unit = {}) {
        setAnimation(AnimationSet(true).apply {
            listOf(
                ScaleAnimation(transition.scaleFrom, transition.scaleTo, transition.scaleFrom, transition.scaleTo),
                AlphaAnimation(transition.alphaFrom, transition.alphaTo)
            ).forEach {
                it.setDuration(transition.durationMs)
                it.setInterpolator(transition.interpolator())
                addAnimation(it)
            }
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart() {}
                override fun onAnimationEnd() = onEnd()
            })
        })
        startAnimation()
    }

    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                AMapLatLng(lat, lng),
                zoom
            )
        )
        else map.animateCamera(CameraUpdateFactory.newLatLng(AMapLatLng(lat, lng)))
    }

    override fun fitBounds(
        points: List<Pair<Double, Double>>,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    ) {
        if (points.isEmpty()) return
        val builder = com.amap.api.maps.model.LatLngBounds.Builder()
        points.forEach { builder.include(AMapLatLng(it.first, it.second)) }
        try {
            val bounds = builder.build()
            try {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBoundsRect(
                        bounds,
                        paddingLeft,
                        paddingRight,
                        paddingTop,
                        paddingBottom
                    )
                )
            } catch (t: Throwable) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        maxOf(paddingLeft, paddingRight, paddingTop, paddingBottom)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun screenLocation(lat: Double, lng: Double): Point? = map.projection?.toScreenLocation(AMapLatLng(lat, lng))

    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                AMapLatLng(lat, lng),
                zoom
            )
        )
        else map.moveCamera(CameraUpdateFactory.newLatLng(AMapLatLng(lat, lng)))
    }

    override val cameraTargetLat: Double? get() = map.cameraPosition?.target?.latitude
    override val cameraTargetLng: Double? get() = map.cameraPosition?.target?.longitude
    override val cameraZoom: Float? get() = map.cameraPosition?.zoom

    private var cameraFinishListener: ((Double, Double) -> Unit)? = null
    private var cameraMoveListener: ((Double, Double) -> Unit)? = null

    private var isTouching = false

    init {
        map.setOnMarkerClickListener { true }
        map.setOnMapTouchListener { event ->
            isTouching = event.actionMasked != android.view.MotionEvent.ACTION_UP &&
                event.actionMasked != android.view.MotionEvent.ACTION_CANCEL
        }
        map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(p0: CameraPosition?) {
                if (!isTouching) return
                p0?.target?.let { cameraMoveListener?.invoke(it.latitude, it.longitude) }
            }

            override fun onCameraChangeFinish(p0: CameraPosition?) {
                p0?.target?.let { cameraFinishListener?.invoke(it.latitude, it.longitude) }
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
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.setAllGesturesEnabled(true)
        map.uiSettings.isRotateGesturesEnabled = false
    }

    override fun setMapType(type: AppMapType) {
        currentMapType = type
        when (type) {
            AppMapType.NORMAL -> {
                map.mapType = if (isDarkMode) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
                map.moveCamera(CameraUpdateFactory.changeTilt(0f))
            }

            AppMapType.SATELLITE -> {
                map.mapType = AMap.MAP_TYPE_SATELLITE
                map.moveCamera(CameraUpdateFactory.changeTilt(0f))
            }

            AppMapType.MAP_3D -> {
                map.mapType = if (isDarkMode) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
                map.moveCamera(CameraUpdateFactory.changeTilt(45f))
            }
        }
    }
}

@Composable
fun AMapViewContainer(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    isDark: Boolean,
    initialCamera: MapCamera?,
    modifier: Modifier,
    onMapReady: (AppMapController) -> Unit
) {
    val amapView = remember {
        val view = TextureMapView(
            context,
            AMapOptions()
                .mapType(if (isDark) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL)
                .apply { initialCamera?.let { camera(CameraPosition(AMapLatLng(it.lat, it.lng), it.zoom, 0f, 0f)) } }
        )
        view.onCreate(Bundle())
        view
    }

    DisposableEffect(lifecycleOwner.lifecycle, amapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> amapView.onResume()
                Lifecycle.Event.ON_PAUSE -> amapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            amapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            amapView.onPause()
            amapView.onDestroy()
        }
    }

    MapAndroidView(amapView, modifier) {
        map.setOnMapLoadedListener {
            val controller = AMapControllerImpl(map, context)
            controller.setDarkMode(isDark, context)
            onMapReady(controller)
        }
    }
}
