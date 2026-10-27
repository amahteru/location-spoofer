package com.vincenthzr.locationspoofer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.vincenthzr.locationspoofer.data.R

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
