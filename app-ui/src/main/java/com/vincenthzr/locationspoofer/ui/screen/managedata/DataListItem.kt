package com.vincenthzr.locationspoofer.ui.screen.managedata

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.db.CompleteLocation
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 采集数据列表项：M3 Filled Card，点击定位到地图；收藏 / 编辑 / 删除收在右侧的溢出菜单里。
 */
@Composable
fun DataListItem(
    item: CompleteLocation,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timeStr =
        remember(item.location.timestamp) { dateFormat.format(Date(item.location.timestamp)) }

    val wifiCount = (if (item.connectedWifi != null) 1 else 0) + item.wifis.size
    val cellCount = item.cells.size
    val btCount = item.bluetooths.size

    val hasPlaceName = item.location.placeName.isNotBlank()
    val hasRemark = item.location.remark.isNotBlank()

    // 优先显示地名或备注为卡片的主标题
    val primaryTitle = when {
        hasPlaceName -> item.location.placeName
        hasRemark -> item.location.remark
        else -> stringResource(R.string.coord_record_title)
    }
    val subtitle = if (hasPlaceName && hasRemark) item.location.remark else null

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${String.format(Locale.US, "%.5f", item.location.lat)}, " +
                        "${String.format(Locale.US, "%.5f", item.location.lng)}  ·  $timeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 信号设备标签组（Wi-Fi、基站、蓝牙）
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SignalChip(Icons.Rounded.Wifi, "$wifiCount", LabelTone.Primary)
                    SignalChip(Icons.Rounded.CellTower, "$cellCount", LabelTone.Warning)
                    SignalChip(Icons.Rounded.Bluetooth, "$btCount", LabelTone.Tertiary)
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    val favoriteLabel = stringResource(
                        if (isFavorited) R.string.remove_from_favorites else R.string.add_to_favorites
                    )
                    DropdownMenuItem(
                        text = { Text(favoriteLabel) },
                        leadingIcon = {
                            Icon(if (isFavorited) Icons.Rounded.Star else Icons.Rounded.StarOutline, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onFavorite()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SignalChip(icon: ImageVector, text: String, tone: LabelTone) {
    StatusLabel(text = text, icon = icon, tone = tone)
}
