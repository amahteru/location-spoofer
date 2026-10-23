package com.vincenthzr.locationspoofer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.db.CompleteLocation
import com.vincenthzr.locationspoofer.data.model.SavedLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalEnvironmentDataDialog(
    dataList: List<CompleteLocation>,
    isLoading: Boolean,
    savedLocations: List<SavedLocation>,
    onSelectPoint: (item: CompleteLocation) -> Unit,
    onFavorite: (item: CompleteLocation) -> Unit,
    onImportClick: () -> Unit,
    onDismiss: () -> Unit,
    collectionRoutes: List<com.vincenthzr.locationspoofer.data.db.CompleteCollectionRoute> = emptyList(),
    onSelectRoute: (com.vincenthzr.locationspoofer.data.db.CompleteCollectionRoute) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // 优先按 sourceLocationId 判断是否已收藏（编辑坐标后依然认得出）；
    // 没有来源 id 的老收藏才退回按坐标匹配。
    val favoritedLocationIds = remember(savedLocations) {
        savedLocations.mapNotNullTo(HashSet()) { it.sourceLocationId }
    }
    val favoritedCoordsFallback = remember(savedLocations) {
        savedLocations.filter { it.sourceLocationId == null }
            .mapTo(HashSet()) { it.lat to it.lng }
    }

    val filteredList = remember(dataList, searchQuery) {
        if (searchQuery.isBlank()) {
            dataList
        } else {
            val query = searchQuery.trim().lowercase()
            dataList.filter { item ->
                item.location.remark.lowercase().contains(query) ||
                        item.location.placeName.lowercase().contains(query) ||
                        item.location.lat.toString().contains(query) ||
                        item.location.lng.toString().contains(query) ||
                        "${item.location.lat},${item.location.lng}".contains(query)
            }
        }
    }

    val filteredRoutes = remember(collectionRoutes, searchQuery) {
        val query = searchQuery.trim()
        collectionRoutes.filter { query.isBlank() || it.route.name.contains(query, true) || it.route.remark.contains(query, true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            // 标题与导入操作
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.local_collected_datasource),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (dataList.isEmpty() && collectionRoutes.isEmpty()) stringResource(R.string.no_local_env_data) else stringResource(R.string.local_collection_records_count, dataList.size, collectionRoutes.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (dataList.isNotEmpty() || collectionRoutes.isNotEmpty()) {
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.import_external_data_json))
                    }
                }
            }

            // 搜索过滤框
            if (dataList.isNotEmpty() || collectionRoutes.isNotEmpty()) {
                AppSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search_remark_coord_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

        // 采集点列表
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (dataList.isEmpty() && collectionRoutes.isEmpty()) {
            // 空数据状态
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.FolderOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.no_local_env_data),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.scan_or_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onImportClick,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.import_external_data_json))
                }
            }
        } else if (filteredList.isEmpty() && filteredRoutes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_matching_collected_points),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRoutes, key = { "collection-route-${it.route.id}" }) { route ->
                    CollectionRouteListItem(route, onClick = { onSelectRoute(route) })
                }
                items(filteredList, key = { it.location.id }) { item ->
                    LocalDataItem(
                        item = item,
                        timeStr = timeFormat.format(Date(item.location.timestamp)),
                        isFavorited = favoritedLocationIds.contains(item.location.id) ||
                            favoritedCoordsFallback.contains(item.location.lat to item.location.lng),
                        onClick = {
                            onSelectPoint(item)
                            onDismiss()
                        },
                        onFavorite = { onFavorite(item) }
                    )
                }
            }
        }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LocalDataItem(
    item: CompleteLocation,
    timeStr: String,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    val hasRemark = item.location.remark.isNotBlank()
    val hasPlaceName = item.location.placeName.isNotBlank()
    // 与「管理采集数据」一致：优先地名，其次备注；两者都有时备注作为副标题
    val primaryTitle = when {
        hasPlaceName -> item.location.placeName
        hasRemark -> item.location.remark
        else -> stringResource(R.string.coord_record_title)
    }
    val subtitle = if (hasPlaceName && hasRemark) item.location.remark else null

    val wifiCount = (if (item.connectedWifi != null) 1 else 0) + item.wifis.size
    val cellCount = item.cells.size
    val btCount = item.bluetooths.size

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 主标题与时间。标题不再限死单行，但仍要设上限，
                // 否则几百字的长备注会把卡片撑得很高；顶部对齐而不是垂直居中，
                // 这样标题一旦换行，时间戳依旧停在第一行同一水平线上，不会飘到文字块中间。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = primaryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 备注副标题
                if (subtitle != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 经纬度
                Text(
                    text = "${String.format(Locale.US, "%.5f", item.location.lat)}, ${
                        String.format(Locale.US, "%.5f", item.location.lng)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 信号设备标签组（按类型分色，与管理页保持一致）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (wifiCount > 0) {
                        StatusLabel(text = "$wifiCount", icon = Icons.Rounded.Wifi, tone = LabelTone.Primary)
                    }
                    if (cellCount > 0) {
                        StatusLabel(text = "$cellCount", icon = Icons.Rounded.CellTower, tone = LabelTone.Warning)
                    }
                    if (btCount > 0) {
                        StatusLabel(text = "$btCount", icon = Icons.Rounded.Bluetooth, tone = LabelTone.Tertiary)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            IconToggleButton(checked = isFavorited, onCheckedChange = { onFavorite() }) {
                Icon(
                    imageVector = if (isFavorited) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = stringResource(
                        if (isFavorited) R.string.remove_from_favorites else R.string.add_to_favorites
                    )
                )
            }
        }
    }
}
