package io.github.amahteru.locationspoofer.ui.screen.tabs.route

import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun RouteControlButton(
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
