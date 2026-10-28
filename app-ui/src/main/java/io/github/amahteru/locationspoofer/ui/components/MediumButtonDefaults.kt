package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

object MediumButtonDefaults {
    val Height = 56.dp
    val ContentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    val IconSize = 24.dp
    val IconSpacing = 8.dp

    val TextStyle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium
}
