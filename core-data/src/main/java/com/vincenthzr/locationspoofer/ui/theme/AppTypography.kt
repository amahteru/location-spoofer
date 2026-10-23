package com.vincenthzr.locationspoofer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.vincenthzr.locationspoofer.data.R

/**
 * MD3 基线字体 Roboto（可变字体，OFL 授权，见 assets/licenses/Roboto-OFL.txt）。
 * 内置而非使用系统字体，避免国产 ROM 的厂商字体替换；中文字形仍回退到系统 CJK 字体。
 * 主界面与悬浮摇杆窗共用，故放在两者都依赖的 core-data 模块。
 */
@OptIn(ExperimentalTextApi::class)
private fun roboto(weight: FontWeight) = Font(
    resId = R.font.roboto,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val AppFontFamily = FontFamily(
    roboto(FontWeight.Normal),
    roboto(FontWeight.Medium),
    roboto(FontWeight.Bold)
)

private val Baseline = Typography()

private fun TextStyle.roboto() = copy(fontFamily = AppFontFamily)

// M3 标准字阶（Display ~ Label 共 15 级），字号、行高、字重、字距沿用官方基线，仅替换字体族
val AppTypography = Typography(
    displayLarge = Baseline.displayLarge.roboto(),
    displayMedium = Baseline.displayMedium.roboto(),
    displaySmall = Baseline.displaySmall.roboto(),
    headlineLarge = Baseline.headlineLarge.roboto(),
    headlineMedium = Baseline.headlineMedium.roboto(),
    headlineSmall = Baseline.headlineSmall.roboto(),
    titleLarge = Baseline.titleLarge.roboto(),
    titleMedium = Baseline.titleMedium.roboto(),
    titleSmall = Baseline.titleSmall.roboto(),
    bodyLarge = Baseline.bodyLarge.roboto(),
    bodyMedium = Baseline.bodyMedium.roboto(),
    bodySmall = Baseline.bodySmall.roboto(),
    labelLarge = Baseline.labelLarge.roboto(),
    labelMedium = Baseline.labelMedium.roboto(),
    labelSmall = Baseline.labelSmall.roboto()
)
