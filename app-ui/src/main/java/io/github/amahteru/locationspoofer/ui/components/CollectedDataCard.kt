package io.github.amahteru.locationspoofer.ui.components

import io.github.amahteru.locationspoofer.data.db.displayName
import io.github.amahteru.locationspoofer.data.db.coordinateText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.amahteru.locationspoofer.data.db.CompleteCollectionRoute
import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.data.model.EnvironmentResolver
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CollectedLocationCard(
    item: CompleteLocation,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val placeName = item.location.placeName
    val remark = item.location.remark
    CollectedDataCard(
        icon = Icons.Rounded.Place,
        title = item.location.displayName(stringResource(R.string.coord_record_title)),
        subtitle = remark.takeIf { placeName.isNotBlank() && it.isNotBlank() },
        detail = "${item.location.coordinateText}\n${formatTime(item.location.timestamp)}",
        records = listOf(item),
        onClick = onClick,
        trailing = trailing
    )
}

@Composable
fun CollectedRouteCard(
    item: CompleteCollectionRoute,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val summary = stringResource(R.string.collection_route_summary, item.samples.size, item.route.halfWidthM.toInt())
    CollectedDataCard(
        icon = Icons.Rounded.Route,
        title = item.route.name.ifBlank { stringResource(R.string.collection_route_unnamed) },
        subtitle = item.route.remark.takeIf { it.isNotBlank() },
        detail = "$summary\n${formatTime(item.route.timestamp)}",
        records = item.samples,
        onClick = onClick,
        trailing = trailing
    )
}

@Composable
fun CollectedDataMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    favorited: Boolean? = null,
    onFavorite: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (favorited != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (favorited) R.string.remove_from_favorites else R.string.add_to_favorites)) },
                    leadingIcon = {
                        Icon(if (favorited) Icons.Rounded.Star else Icons.Rounded.StarOutline, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onFavorite()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun CollectedDataCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    detail: String,
    records: List<CompleteLocation>,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?
) {
    val counts = remember(records) { EnvironmentResolver.signalCounts(records) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { Icon(icon, contentDescription = null) },
            headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    if (subtitle != null) Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(detail)
                    Row(
                        modifier = Modifier.padding(top = AppSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        StatusLabel(text = "${counts.wifi}", icon = Icons.Rounded.Wifi, tone = LabelTone.Primary)
                        StatusLabel(text = "${counts.cell}", icon = Icons.Rounded.CellTower, tone = LabelTone.Warning)
                        StatusLabel(text = "${counts.bluetooth}", icon = Icons.Rounded.Bluetooth, tone = LabelTone.Tertiary)
                    }
                }
            },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun formatTime(timestamp: Long): String {
    val format = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    return remember(timestamp) { format.format(Date(timestamp)) }
}
