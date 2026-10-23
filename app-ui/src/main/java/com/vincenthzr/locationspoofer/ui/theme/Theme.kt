package com.vincenthzr.locationspoofer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 应用主题：Material 3 配色（Android 12+ 动态取色，定义在 core-data）、M3 标准字阶（AppTypography，定义在 core-data）与 M3 形状阶梯。
 */
@Composable
fun AppTheme(isDark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = appColorScheme(isDark),
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

// M3 形状阶梯：extraSmall 4 / small 8 / medium 12 / large 16 / extraLarge 28
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun appColorScheme(isDark: Boolean): ColorScheme = appColorScheme(LocalContext.current, isDark)
