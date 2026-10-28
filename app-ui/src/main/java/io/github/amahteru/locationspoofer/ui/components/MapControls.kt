package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing
import kotlin.math.roundToInt

@Composable
fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.loweredElevation()
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun BoxScope.MapControlColumn(
    anchorTop: Float,
    containerBottom: Float,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = AppSpacing.lg, bottom = AppSpacing.lg)
            .offset { IntOffset(0, (anchorTop - containerBottom).roundToInt()) },
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        content = content
    )
}
