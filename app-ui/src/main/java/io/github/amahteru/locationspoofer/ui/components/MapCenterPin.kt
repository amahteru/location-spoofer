package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val PinSize = 32.dp

@Composable
fun MapCenterPin(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .padding(bottom = PinSize * 5 / 6)
            .size(PinSize)
    )
}
