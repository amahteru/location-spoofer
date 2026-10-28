package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    insideMargin: PaddingValues = PaddingValues(AppSpacing.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier, shape = shape) {
        Column(modifier = Modifier.padding(insideMargin), content = content)
    }
}
