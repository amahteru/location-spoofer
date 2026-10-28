package io.github.amahteru.locationspoofer.ui.components

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
