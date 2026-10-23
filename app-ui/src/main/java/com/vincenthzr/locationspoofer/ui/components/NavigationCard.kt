package com.vincenthzr.locationspoofer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 列表项前导的色调图标容器（40dp 圆形，xxxContainer 底色） */
@Composable
fun ToneIcon(icon: ImageVector, tone: LabelTone = LabelTone.Secondary) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.containerOf(tone),
        contentColor = colors.onContainerOf(tone),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * 可点击的导航列表项：前导图标 + 标题 + 说明 + 尾部（默认右箭头）。透明底色，放在卡片或页面里均可。
 */
@Composable
fun NavigationListItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    tone: LabelTone = LabelTone.Secondary,
    trailing: (@Composable () -> Unit)? = {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { ToneIcon(icon, tone) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** 单独成卡的导航入口：Filled Card 包一行 [NavigationListItem] */
@Composable
fun NavigationCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    tone: LabelTone = LabelTone.Secondary,
    trailing: (@Composable () -> Unit)? = {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        NavigationListItem(
            icon = icon,
            title = title,
            onClick = onClick,
            summary = summary,
            tone = tone,
            trailing = trailing
        )
    }
}
