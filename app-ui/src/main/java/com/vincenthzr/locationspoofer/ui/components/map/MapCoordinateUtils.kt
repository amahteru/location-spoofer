package com.vincenthzr.locationspoofer.ui.components.map

import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.vincenthzr.locationspoofer.ui.components.MarkerType
import com.vincenthzr.locationspoofer.utils.CoordinateUtils

fun wgs84ToGcj02(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.wgs84ToGcj02(wgsLat, wgsLng)
    return Pair(r.lat, r.lng)
}

fun gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.gcj02ToWgs84(gcjLat, gcjLng)
    return Pair(r.lat, r.lng)
}

fun bd09ToGcj02(bdLat: Double, bdLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.bd09ToGcj02(bdLat, bdLng)
    return Pair(r.lat, r.lng)
}

fun gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val r = CoordinateUtils.gcj02ToBd09(gcjLat, gcjLng)
    return Pair(r.lat, r.lng)
}

object GaodeMarkerHelper {
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun getMarkerBitmap(
        context: Context,
        text: String,
        type: MarkerType
    ): Bitmap {
        val cacheKey = "${type.name}_$text"
        bitmapCache[cacheKey]?.let {
            if (!it.isRecycled) return it
        }

        val density = context.resources.displayMetrics.density
        val width = (32 * density).toInt().coerceAtLeast(1)
        val height = (44 * density).toInt().coerceAtLeast(1)

        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val mainColor = when (type) {
            MarkerType.GREEN -> "#00B578".toColorInt()
            MarkerType.RED -> "#FA5151".toColorInt()
            MarkerType.ORANGE -> "#FF7A00".toColorInt()
            MarkerType.DEFAULT -> "#388BFD".toColorInt()
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainColor
            style = Paint.Style.FILL
            isDither = true
        }

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#33000000".toColorInt()
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            RectF(
                width * 0.2f,
                height * 0.88f,
                width * 0.8f,
                height * 0.98f
            ),
            shadowPaint
        )

        val path = Path()
        val radius = width / 2f
        path.moveTo(radius, height * 0.88f)
        path.cubicTo(
            width * 0.95f, height * 0.55f,
            width.toFloat(), height * 0.35f,
            width.toFloat(), radius
        )
        path.arcTo(RectF(0f, 0f, width.toFloat(), width.toFloat()), 0f, -180f, false)
        path.cubicTo(
            0f, height * 0.35f,
            width * 0.05f, height * 0.55f,
            radius, height * 0.88f
        )
        path.close()
        canvas.drawPath(path, paint)

        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius * 0.65f, whitePaint)

        if (text.isNotBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mainColor
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = if (text.length > 2) radius * 0.65f else radius * 0.85f
            }
            val fontMetrics = textPaint.fontMetrics
            val baseline = radius - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(text, radius, baseline, textPaint)
        }

        bitmapCache[cacheKey] = bitmap
        return bitmap
    }
}
