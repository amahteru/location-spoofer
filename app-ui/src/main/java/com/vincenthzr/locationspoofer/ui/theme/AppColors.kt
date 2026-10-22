package com.vincenthzr.locationspoofer.ui.theme

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 品牌种子色：Android 12 以下无系统动态取色时，用它按 MD3 Tonal Spot 算法生成整套配色
val SeedBlue = Color(0xFF388BFD)

// 强调色跟随当前配色方案的 primary（动态取色时即系统壁纸色）
val AccentBlue: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

// 位于 AccentBlue 背景之上的文字与图标颜色
val OnAccentBlue: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onPrimary

// 语义状态色（成功 / 警告），不随动态取色变化
val AccentGreen = Color(0xFF2EA043)
val AccentOrange = Color(0xFFD29922)

// 由 SeedBlue 通过 material-color-utilities（Tonal Spot，2021 规范）生成
val AppColorSchemeLight = lightColorScheme(
    primary = Color(0xFF415E91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF284777),
    inversePrimary = Color(0xFFAAC7FF),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF3E4759),
    tertiary = Color(0xFF705574),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF573E5C),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceTint = Color(0xFF415E91),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceDim = Color(0xFFD9D9E0),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

val AppColorSchemeDark = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0B305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD7E3FF),
    inversePrimary = Color(0xFF415E91),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFAAC7FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainerLowest = Color(0xFF0C0E13)
)

@Composable
fun appColorScheme(isDark: Boolean): ColorScheme {
    val context = LocalContext.current
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> AppColorSchemeDark
        else -> AppColorSchemeLight
    }
}

object AppColors {
    @Composable @ReadOnlyComposable
    fun textSecondary(@Suppress("UNUSED_PARAMETER") isDark: Boolean) = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable @ReadOnlyComposable
    fun surface(@Suppress("UNUSED_PARAMETER") isDark: Boolean) = MaterialTheme.colorScheme.surface

    @Composable @ReadOnlyComposable
    fun cardBackground(@Suppress("UNUSED_PARAMETER") isDark: Boolean) = MaterialTheme.colorScheme.surfaceContainer

    @Composable @ReadOnlyComposable
    fun topBarBackground(@Suppress("UNUSED_PARAMETER") isDark: Boolean) = MaterialTheme.colorScheme.surfaceContainer

    @Composable @ReadOnlyComposable
    fun background(@Suppress("UNUSED_PARAMETER") isDark: Boolean) = MaterialTheme.colorScheme.background
}

/**
 * 彻底取消点击水波纹/高亮特效的全局 Modifier 扩展
 */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
