package io.github.amahteru.locationspoofer.ui.components.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.animation.PathInterpolator
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.graphics.withScale
import io.github.amahteru.locationspoofer.ui.components.MapMarkerStyle

internal enum class MarkerTransition(val alphaFrom: Float, val durationMs: Long, private val curve: FloatArray) {
    Enter(0f, 400, floatArrayOf(0.05f, 0.7f, 0.1f, 1f)),
    Exit(1f, 200, floatArrayOf(0.3f, 0f, 0.8f, 0.15f));

    val alphaTo get() = 1f - alphaFrom
    val scaleFrom get() = 0.8f + 0.2f * alphaFrom
    val scaleTo get() = 0.8f + 0.2f * alphaTo
    fun interpolator() = PathInterpolator(curve[0], curve[1], curve[2], curve[3])
}

internal object MapMarkerIcons {
    private val markers = mutableMapOf<MapMarkerStyle, Bitmap>()
    private val lines = mutableMapOf<Int, Bitmap>()

    const val ANCHOR_Y = (PIN_TIP - PIN_TOP) / PIN_HEIGHT

    fun marker(context: Context, style: MapMarkerStyle): Bitmap = markers.getOrPut(style) {
        pin(2 * context.resources.displayMetrics.density, style)
    }

    val lineTextureScale = LINE_TEXTURE_SIZE / (LINE_TEXTURE_SIZE - 2f * LINE_TEXTURE_EDGE)

    fun line(color: Int): Bitmap = lines.getOrPut(color) {
        createBitmap(LINE_TEXTURE_SIZE, LINE_TEXTURE_SIZE).apply {
            for (x in 0 until LINE_TEXTURE_SIZE) {
                val alpha = minOf(1f, (minOf(x, LINE_TEXTURE_SIZE - 1 - x) + 0.5f) / LINE_TEXTURE_EDGE)
                val pixel = Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
                for (y in 0 until LINE_TEXTURE_SIZE) this[x, y] = pixel
            }
        }
    }

    private fun pin(unit: Float, style: MapMarkerStyle): Bitmap =
        createBitmap((PIN_WIDTH * unit).toInt(), (PIN_HEIGHT * unit).toInt()).also { bitmap ->
            val canvas = Canvas(bitmap)
            val cx = (HEAD_X - PIN_LEFT) * unit
            val cy = (HEAD_Y - PIN_TOP) * unit
            canvas.withScale(unit, unit) {
                translate(-PIN_LEFT, -PIN_TOP)
                drawPath(PIN_PATH, paint(style.color))
            }
            canvas.drawCircle(cx, cy, 5.5f * unit, paint(style.onColor))
            val text = paint(style.color).apply {
                typeface = Typeface.create(Typeface.DEFAULT, 500, false)
                textSize = unit * if (style.label.length > 2) 5f else 6.5f
            }
            val bounds = Rect().also { text.getTextBounds(style.label, 0, style.label.length, it) }
            canvas.drawText(style.label, cx - bounds.exactCenterX(), cy - bounds.exactCenterY(), text)
        }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private val PIN_PATH = PathParser.createPathFromPathData(
        "M12,2c-4.2,0 -8,3.22 -8,8.2c0,3.18 2.45,6.92 7.34,11.23c0.38,0.33 0.95,0.33 1.33,0C17.55,17.12 20,13.38 20,10.2C20,5.22 16.2,2 12,2z"
    )
}

private const val LINE_TEXTURE_SIZE = 32
private const val LINE_TEXTURE_EDGE = 3
private const val PIN_LEFT = 4f
private const val PIN_TOP = 2f
private const val PIN_TIP = 21.68f
private const val HEAD_X = 12f
private const val HEAD_Y = 10.2f
private const val PIN_WIDTH = 16f
private const val PIN_HEIGHT = 20f
