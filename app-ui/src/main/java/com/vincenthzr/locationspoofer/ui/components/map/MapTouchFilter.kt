package com.vincenthzr.locationspoofer.ui.components.map

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

private class MapTouchFilterLayout(context: Context) : FrameLayout(context) {
    var routedPositions: List<Offset> = emptyList()
    private val blocked = HashSet<Int>()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) blocked.clear()
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val x = ev.getX(ev.actionIndex)
            val y = ev.getY(ev.actionIndex)
            if (routedPositions.none { abs(it.x - x) < 2f && abs(it.y - y) < 2f }) {
                blocked.add(ev.getPointerId(ev.actionIndex))
            }
        }
        val handled = if (blocked.isEmpty()) {
            super.dispatchTouchEvent(ev)
        } else {
            withoutBlocked(ev)?.let { filtered -> super.dispatchTouchEvent(filtered).also { filtered.recycle() } } ?: true
        }
        if (action == MotionEvent.ACTION_POINTER_UP) blocked.remove(ev.getPointerId(ev.actionIndex))
        return handled
    }

    private fun withoutBlocked(ev: MotionEvent): MotionEvent? {
        val keep = (0 until ev.pointerCount).filter { ev.getPointerId(it) !in blocked }
        if (keep.isEmpty()) return null
        val masked = ev.actionMasked
        val action = when (masked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                val index = keep.indexOf(ev.actionIndex)
                if (index < 0) return null
                when {
                    keep.size > 1 -> masked or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    masked == MotionEvent.ACTION_POINTER_DOWN -> MotionEvent.ACTION_DOWN
                    else -> MotionEvent.ACTION_UP
                }
            }
            else -> masked
        }
        val properties = Array(keep.size) { i ->
            MotionEvent.PointerProperties().also { ev.getPointerProperties(keep[i], it) }
        }
        val coords = Array(keep.size) { i ->
            MotionEvent.PointerCoords().also { ev.getPointerCoords(keep[i], it) }
        }
        return MotionEvent.obtain(
            ev.downTime, ev.eventTime, action, keep.size, properties, coords,
            ev.metaState, ev.buttonState, ev.xPrecision, ev.yPrecision,
            ev.deviceId, ev.edgeFlags, ev.source, ev.flags
        )
    }
}

@Composable
internal fun <T : View> MapAndroidView(mapView: T, modifier: Modifier, setup: T.() -> Unit) {
    val layout = remember(mapView) { MapTouchFilterLayout(mapView.context) }
    AndroidView(
        factory = { layout.apply { addView(mapView.apply(setup)) } },
        modifier = modifier.pointerInput(layout) {
            awaitPointerEventScope {
                while (true) {
                    layout.routedPositions = awaitPointerEvent(PointerEventPass.Initial).changes.map { it.position }
                }
            }
        }
    )
}
