package io.github.amahteru.locationspoofer.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val ColorScheme.isDark: Boolean get() = surface.luminance() < 0.5f

val ColorScheme.success: Color get() = if (isDark) Color(0xFF9DD67D) else Color(0xFF386A20)
val ColorScheme.onSuccess: Color get() = if (isDark) Color(0xFF0B3900) else Color(0xFFFFFFFF)
val ColorScheme.successContainer: Color get() = if (isDark) Color(0xFF205107) else Color(0xFFB8F397)
val ColorScheme.onSuccessContainer: Color get() = if (isDark) Color(0xFFB8F397) else Color(0xFF042100)

val ColorScheme.warning: Color get() = if (isDark) Color(0xFFF7BD4A) else Color(0xFF7C5800)
val ColorScheme.onWarning: Color get() = if (isDark) Color(0xFF412D00) else Color(0xFFFFFFFF)
val ColorScheme.warningContainer: Color get() = if (isDark) Color(0xFF5E4200) else Color(0xFFFFDEA6)
val ColorScheme.onWarningContainer: Color get() = if (isDark) Color(0xFFFFDEA6) else Color(0xFF271900)

fun appColorScheme(context: Context, isDark: Boolean): ColorScheme =
    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
