package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.theme.onSuccessContainer
import io.github.amahteru.locationspoofer.ui.theme.onWarningContainer
import io.github.amahteru.locationspoofer.ui.theme.successContainer
import io.github.amahteru.locationspoofer.ui.theme.warningContainer
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

enum class LabelTone { Primary, Secondary, Tertiary, Success, Warning, Error, Neutral }

fun ColorScheme.containerOf(tone: LabelTone): Color = when (tone) {
    LabelTone.Primary -> primaryContainer
    LabelTone.Secondary -> secondaryContainer
    LabelTone.Tertiary -> tertiaryContainer
    LabelTone.Success -> successContainer
    LabelTone.Warning -> warningContainer
    LabelTone.Error -> errorContainer
    LabelTone.Neutral -> surfaceContainerHighest
}

fun ColorScheme.onContainerOf(tone: LabelTone): Color = when (tone) {
    LabelTone.Primary -> onPrimaryContainer
    LabelTone.Secondary -> onSecondaryContainer
    LabelTone.Tertiary -> onTertiaryContainer
    LabelTone.Success -> onSuccessContainer
    LabelTone.Warning -> onWarningContainer
    LabelTone.Error -> onErrorContainer
    LabelTone.Neutral -> onSurfaceVariant
}

@Composable
fun StatusLabel(
    text: String,
    modifier: Modifier = Modifier,
    tone: LabelTone = LabelTone.Primary,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.containerOf(tone),
        contentColor = colors.onContainerOf(tone)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
