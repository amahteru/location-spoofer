package com.vincenthzr.locationspoofer.ui.screen.tabs.location

import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun MapControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null
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
