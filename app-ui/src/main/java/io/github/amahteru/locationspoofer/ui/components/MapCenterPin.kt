package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.theme.AppMotion

private val PinSize = 32.dp

@Composable
fun BoxScope.MapCenterPin(visible: Boolean, icon: ImageVector, tint: Color) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(AppMotion.defaultEffectsSpec()) + scaleIn(AppMotion.defaultSpatialSpec(), initialScale = 0.8f),
        exit = fadeOut(AppMotion.fastEffectsSpec()) + scaleOut(AppMotion.fastSpatialSpec(), targetScale = 0.8f)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(bottom = PinSize * 5 / 6)
                .size(PinSize)
        )
    }
}
