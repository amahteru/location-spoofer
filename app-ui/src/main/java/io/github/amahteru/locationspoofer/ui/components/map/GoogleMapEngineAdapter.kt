package io.github.amahteru.locationspoofer.ui.components.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Point
import androidx.core.animation.doOnEnd
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.RoundCap
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory as GCameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView as GMapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory as GBitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng as GLatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle
import com.google.android.gms.maps.model.MarkerOptions as GMarkerOptions
import com.google.android.gms.maps.model.PolylineOptions as GPolylineOptions
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppMapType
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.AppMapMarker
import io.github.amahteru.locationspoofer.ui.components.AppMapOverlay
import io.github.amahteru.locationspoofer.ui.components.MapCamera
import io.github.amahteru.locationspoofer.ui.components.MapMarkerStyle

class GMapControllerImpl(
    private val map: GoogleMap,
    private val mapView: android.view.View,
    private val context: Context
) : AppMapController {
    private var currentMapType: AppMapType = AppMapType.NORMAL

    override fun setDarkMode(isDark: Boolean, context: Context) {
        try {
            if (isDark) {
                map.setMapStyle(
                    loadRawResourceStyle(
                        context,
                        R.raw.map_style_dark
                    )
                )
            } else {
                map.setMapStyle(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setMapType(currentMapType)
    }

    private val markerAnimators = mutableSetOf<ValueAnimator>()

    override fun clear() {
        markerAnimators.forEach { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }
        markerAnimators.clear()
        map.clear()
    }

    private val density = context.resources.displayMetrics.density

    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, widthDp: Float): AppMapOverlay {
        val polyline = map.addPolyline(
            GPolylineOptions().color(colorInt).width(widthDp * density).startCap(RoundCap()).endCap(RoundCap()).jointType(JointType.ROUND).apply {
                points.forEach { add(GLatLng(it.first, it.second)) }
            }
        )
        return object : AppMapOverlay {
            override fun remove() {
                polyline.remove()
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
            CircleOptions()
                .center(GLatLng(lat, lng))
                .radius(radius)
                .fillColor(fillColorInt)
                .strokeColor(strokeColorInt)
                .strokeWidth(strokeWidthDp * density)
        )
    }

    override fun addMarker(lat: Double, lng: Double, style: MapMarkerStyle, animate: Boolean): AppMapMarker {
        val marker = map.addMarker(
            GMarkerOptions()
                .position(GLatLng(lat, lng))
                .icon(icon(style))
                .anchor(0.5f, MapMarkerIcons.ANCHOR_Y)
                .zIndex(style.zIndex.toFloat())
        )!!
        if (animate) marker.play(MarkerTransition.Enter)
        return object : AppMapMarker {
            var current = style

            override fun setPosition(lat: Double, lng: Double) {
                marker.position = GLatLng(lat, lng)
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

    private fun icon(style: MapMarkerStyle) = GBitmapDescriptorFactory.fromBitmap(MapMarkerIcons.marker(context, style))

    private fun Marker.play(transition: MarkerTransition, onEnd: () -> Unit = {}) {
        alpha = transition.alphaFrom
        ValueAnimator.ofFloat(transition.alphaFrom, transition.alphaTo).apply {
            duration = transition.durationMs
            interpolator = transition.interpolator()
            addUpdateListener { alpha = it.animatedValue as Float }
            doOnEnd {
                markerAnimators -= this
                onEnd()
            }
            markerAnimators += this
            start()
        }
    }

    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.animateCamera(GCameraUpdateFactory.newLatLngZoom(GLatLng(lat, lng), zoom))
        else map.animateCamera(GCameraUpdateFactory.newLatLng(GLatLng(lat, lng)))
    }

    override fun fitBounds(
        points: List<Pair<Double, Double>>,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    ) {
        if (points.isEmpty()) return
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(GLatLng(it.first, it.second)) }
        try {
            val bounds = builder.build()
            map.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            map.animateCamera(
                GCameraUpdateFactory.newLatLngBounds(bounds, 0),
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() = clearPaddingKeepingView()
                    override fun onCancel() = clearPaddingKeepingView()
                }
            )
        } catch (e: Exception) {
            map.setPadding(0, 0, 0, 0)
            e.printStackTrace()
        }
    }

    private fun clearPaddingKeepingView() {
        val center = map.projection.fromScreenLocation(
            android.graphics.Point(mapView.width / 2, mapView.height / 2)
        )
        map.setPadding(0, 0, 0, 0)
        map.moveCamera(GCameraUpdateFactory.newLatLng(center))
    }

    override fun screenLocation(lat: Double, lng: Double): Point? = map.projection.toScreenLocation(GLatLng(lat, lng))

    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.moveCamera(GCameraUpdateFactory.newLatLngZoom(GLatLng(lat, lng), zoom))
        else map.moveCamera(GCameraUpdateFactory.newLatLng(GLatLng(lat, lng)))
    }

    override val cameraTargetLat: Double? get() = map.cameraPosition.target.latitude
    override val cameraTargetLng: Double? get() = map.cameraPosition.target.longitude
    override val cameraZoom: Float? get() = map.cameraPosition.zoom

    private var cameraFinishListener: ((Double, Double) -> Unit)? = null
    private var cameraMoveListener: ((Double, Double) -> Unit)? = null

    private var isGesture = false

    init {
        map.setOnMarkerClickListener { true }
        map.setOnCameraMoveStartedListener { reason ->
            isGesture = reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
        }
        map.setOnCameraMoveListener {
            if (!isGesture) return@setOnCameraMoveListener
            val target = map.cameraPosition.target
            cameraMoveListener?.invoke(target.latitude, target.longitude)
        }
        map.setOnCameraIdleListener {
            val target = map.cameraPosition.target
            cameraFinishListener?.invoke(target.latitude, target.longitude)
        }
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
        map.mapType = if (type == AppMapType.SATELLITE) GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
        val tilt = if (type == AppMapType.MAP_3D) 45f else 0f
        val newCam = CameraPosition.builder(map.cameraPosition).tilt(tilt).build()
        map.moveCamera(GCameraUpdateFactory.newCameraPosition(newCam))
    }
}

@Composable
fun GoogleMapViewContainer(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    isDark: Boolean,
    initialCamera: MapCamera?,
    modifier: Modifier,
    onMapReady: (AppMapController) -> Unit
) {
    val gmapView = remember {
        GMapView(
            context,
            GoogleMapOptions()
                .backgroundColor(if (isDark) 0xFF242F3E.toInt() else null)
                .apply { initialCamera?.let { camera(CameraPosition(GLatLng(it.lat, it.lng), it.zoom, 0f, 0f)) } }
        ).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycleOwner.lifecycle, gmapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> gmapView.onResume()
                Lifecycle.Event.ON_PAUSE -> gmapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            gmapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            gmapView.onPause()
            gmapView.onDestroy()
        }
    }

    MapAndroidView(gmapView, modifier) {
        getMapAsync { map ->
            val controller = GMapControllerImpl(map, this, context)
            controller.setDarkMode(isDark, context)
            onMapReady(controller)
        }
    }
}
