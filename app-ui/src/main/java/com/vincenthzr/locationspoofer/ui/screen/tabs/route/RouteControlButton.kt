package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** 地图上的悬浮操作按钮：M3 Small FAB（surface 容器色、primary 图标） */
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
