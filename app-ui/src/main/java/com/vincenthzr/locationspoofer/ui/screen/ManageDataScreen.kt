package com.vincenthzr.locationspoofer.ui.screen

import com.vincenthzr.locationspoofer.ui.theme.animateListItem
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import com.vincenthzr.locationspoofer.ui.components.AppTopBar
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.db.CompleteLocation
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.AppMapView
import com.vincenthzr.locationspoofer.ui.screen.managedata.ModernEditDataDialog
import com.vincenthzr.locationspoofer.ui.screen.managedata.DataListItem
import com.vincenthzr.locationspoofer.ui.screen.managedata.ManageDeleteConfirmDialog
import com.vincenthzr.locationspoofer.ui.components.MapCoverageHelper
import com.vincenthzr.locationspoofer.viewmodel.FavoriteToggleResult
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.ManageDataViewModel
import com.vincenthzr.locationspoofer.viewmodel.isDomesticEnvironment
import com.vincenthzr.locationspoofer.data.model.geometry
import com.vincenthzr.locationspoofer.viewmodel.selectCollectedRoute
import com.vincenthzr.locationspoofer.viewmodel.saveCollectionRouteInfo
import com.vincenthzr.locationspoofer.viewmodel.deleteCollectedRoute
import com.vincenthzr.locationspoofer.viewmodel.selectCollectedLocation
import com.vincenthzr.locationspoofer.viewmodel.syncFavoriteCoordinateIfExists
import com.vincenthzr.locationspoofer.viewmodel.toggleCollectedLocationFavorite
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun ManageDataScreen(
    viewModel: MainViewModel,
    uiState: com.vincenthzr.locationspoofer.data.model.AppState,
    onClose: () -> Unit,
    manageDataViewModel: ManageDataViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    var editingItem by remember { mutableStateOf<CompleteLocation?>(null) }
    var itemToDelete by remember { mutableStateOf<CompleteLocation?>(null) }

    val manageDataUiState by manageDataViewModel.uiState.collectAsState()
    val dataList = manageDataUiState.dataList
    val collectionRoutes = manageDataUiState.collectionRoutes
    var editingRoute by remember { mutableStateOf<com.vincenthzr.locationspoofer.data.db.CollectionRouteRecord?>(null) }
    var deletingRoute by remember { mutableStateOf<com.vincenthzr.locationspoofer.data.db.CollectionRouteRecord?>(null) }

    // 优先按 sourceLocationId 判断是否已收藏（编辑坐标后依然认得出）；
    // 没有来源 id 的老收藏才退回按坐标匹配。
    val favoritedLocationIds = remember(uiState.savedLocations) {
        uiState.savedLocations.mapNotNullTo(HashSet()) { it.sourceLocationId }
    }
    val favoritedCoordsFallback = remember(uiState.savedLocations) {
        uiState.savedLocations.filter { it.sourceLocationId == null }
            .mapTo(HashSet()) { it.lat to it.lng }
    }

    BackHandler(onBack = onClose)

    LaunchedEffect(mapController, uiState.mapType) {
        mapController?.setMapType(uiState.mapType)
    }

    LaunchedEffect(mapController, dataList, collectionRoutes) {
        val controller = mapController ?: return@LaunchedEffect
        controller.clear()
        val locations = dataList.map { it.location }
        val last = locations.lastOrNull()
        MapCoverageHelper.drawCoverage(controller, locations, last?.lat, last?.lng)
        collectionRoutes.take(20).forEach { route ->
            com.vincenthzr.locationspoofer.ui.components.CollectionRouteMapHelper.draw(controller, route.route, maxOf(8, 160 / maxOf(1, collectionRoutes.size)))
        }
        if (collectionRoutes.isNotEmpty()) {
            val geometry = collectionRoutes.first().route.geometry()
            if (geometry.isNotEmpty()) controller.fitBounds(geometry.map { it.lat to it.lng }, 80)
        } else if (last != null) {
            controller.moveCamera(last.lat, last.lng, 15f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AppTopBar(
                title = stringResource(R.string.title_manage_data),
                subtitle = stringResource(R.string.total_collected_data_count, (dataList.size + collectionRoutes.size)),
                onBack = onClose,
                actions = {
                    // 手动刷新按钮：兜底给用户一个明确的"我要看最新数据"入口（issue #60）
                    IconButton(
                        onClick = { manageDataViewModel.loadManageData() },
                        enabled = !manageDataUiState.isLoading
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh_data))
                    }
                }
            )

            if (manageDataUiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if ((dataList.isEmpty() && collectionRoutes.isEmpty())) {
                // 空数据状态质感呈现
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.no_data_collected),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.manage_data_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 地图概览卡片
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.36f)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppMapView(
                            mapEngine = uiState.mapEngine,
                            isDomestic = viewModel.isDomesticEnvironment(),
                            modifier = Modifier.fillMaxSize(),
                            onMapReady = { controller ->
                                mapController = controller
                                controller.disableUiControls()
                            }
                        )

                        // 地图右上角覆盖范围提示
                        StatusLabel(
                            text = stringResource(R.string.local_collection_records_count, dataList.size, collectionRoutes.size),
                            tone = LabelTone.Secondary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        )
                    }
                }

                // 下部数据卡片列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.64f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(collectionRoutes, key = { "collection-route-${it.route.id}" }) { route ->
                        Box(animateListItem()) {
                            com.vincenthzr.locationspoofer.ui.components.CollectionRouteListItem(
                                route,
                                onClick = {
                                    viewModel.selectCollectedRoute(route.route.id)
                                    mapController?.let { com.vincenthzr.locationspoofer.ui.components.CollectionRouteMapHelper.show(it, route.route, 120) }
                                },
                                onEdit = { editingRoute = route.route },
                                onDelete = { deletingRoute = route.route }
                            )
                        }
                    }
                    items(dataList, key = { it.location.id }) { item ->
                        Box(animateListItem()) {
                            DataListItem(
                                item = item,
                                isFavorited = favoritedLocationIds.contains(item.location.id) ||
                                    favoritedCoordsFallback.contains(item.location.lat to item.location.lng),
                                onClick = {
                                    viewModel.selectCollectedLocation(item.location.id)
                                    mapController?.animateCamera(
                                        item.location.lat,
                                        item.location.lng,
                                        17f
                                    )
                                },
                                onEdit = { editingItem = item },
                                onDelete = { itemToDelete = item },
                                onFavorite = {
                                    viewModel.toggleCollectedLocationFavorite(item.location.id) { result ->
                                        val message = when (result) {
                                            is FavoriteToggleResult.Added -> context.getString(
                                                R.string.favorited_toast,
                                                result.name
                                            )

                                            is FavoriteToggleResult.Removed -> context.getString(
                                                R.string.unfavorited_toast,
                                                result.name
                                            )

                                            FavoriteToggleResult.Failed -> context.getString(R.string.favorite_failed)
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

            }
        }
    }

    // 现代化编辑数据弹窗
    if (editingItem != null) {
        val currentItem = dataList.find { it.location.id == editingItem?.location?.id } ?: editingItem!!
        ModernEditDataDialog(
            item = currentItem,
            onDismiss = { editingItem = null },
            onSave = { lat, lng, placeName, remark, selectedWifiBssid, selectedBluetoothAddress, selectedCellKey ->
                manageDataViewModel.updateManageDataMetadata(
                    currentItem.location.id,
                    lat,
                    lng,
                    placeName,
                    remark,
                    selectedWifiBssid,
                    selectedBluetoothAddress,
                    selectedCellKey
                )
                // 坐标可能被改了，同步一下这条记录对应的收藏（如果有），
                // 不然收藏会因为坐标变了而找不到关联、变成孤儿数据。
                viewModel.syncFavoriteCoordinateIfExists(currentItem.location.id, lat, lng)
                viewModel.selectCollectedLocation(currentItem.location.id)
                editingItem = null
            },
            onSaveWifi = { bssid, ssid, frequency, level, capabilities, vendor, isConnected, isDesignated ->
                manageDataViewModel.saveOrUpdateLocationWifi(
                    locationId = currentItem.location.id,
                    bssid = bssid,
                    ssid = ssid,
                    frequency = frequency,
                    level = level,
                    capabilities = capabilities,
                    vendor = vendor,
                    isConnected = isConnected,
                    isDesignatedSimulation = isDesignated
                )
            },
            onDeleteWifi = { bssid ->
                manageDataViewModel.deleteLocationWifi(
                    locationId = currentItem.location.id,
                    bssid = bssid
                )
            },
            onSaveCell = { cellKey, type, mcc, mnc, tac, ci, pci, lac, cid, psc, nci, networkId, systemId, basestationId, dbm, isRegistered, isDesignated ->
                manageDataViewModel.saveOrUpdateLocationCell(
                    locationId = currentItem.location.id,
                    cellKey = cellKey,
                    type = type,
                    mcc = mcc,
                    mnc = mnc,
                    tac = tac,
                    ci = ci,
                    pci = pci,
                    lac = lac,
                    cid = cid,
                    psc = psc,
                    nci = nci,
                    networkId = networkId,
                    systemId = systemId,
                    basestationId = basestationId,
                    dbm = dbm,
                    isRegistered = isRegistered,
                    isDesignated = isDesignated
                )
            },
            onDeleteCell = { cellKey ->
                manageDataViewModel.deleteLocationCell(
                    locationId = currentItem.location.id,
                    cellKey = cellKey
                )
            },
            onSaveBluetooth = { address, name, scanRecordHex, rssi, isDesignated ->
                manageDataViewModel.saveOrUpdateLocationBluetooth(
                    locationId = currentItem.location.id,
                    address = address,
                    name = name,
                    scanRecordHex = scanRecordHex,
                    rssi = rssi,
                    isDesignated = isDesignated
                )
            },
            onDeleteBluetooth = { address ->
                manageDataViewModel.deleteLocationBluetooth(
                    locationId = currentItem.location.id,
                    address = address
                )
            }
        )
    }

    editingRoute?.let { route ->
        CollectionRouteInfoDialog(route, uiState.isSavingCollectionInfo,
            onDismiss = { editingRoute = null },
            onSave = { name, remark ->
                viewModel.saveCollectionRouteInfo(route.id, name, remark, closePending = false, onSaved = { editingRoute = null })
            })
    }
    deletingRoute?.let { route ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingRoute = null },
            title = { Text(stringResource(R.string.collection_route_delete_title)) },
            text = { Text(stringResource(R.string.collection_route_delete_message, route.name)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteCollectedRoute(route.id); deletingRoute = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deletingRoute = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // 删除单项采集数据二次确认弹窗
    if (itemToDelete != null) {
        val targetItem = itemToDelete!!
        val displayName = when {
            targetItem.location.placeName.isNotBlank() -> targetItem.location.placeName
            targetItem.location.remark.isNotBlank() -> targetItem.location.remark
            else -> "坐标 (${
                String.format(
                    Locale.US,
                    "%.4f",
                    targetItem.location.lat
                )
            }, ${String.format(Locale.US, "%.4f", targetItem.location.lng)})"
        }

        ManageDeleteConfirmDialog(
            title = stringResource(R.string.delete_data_title),
            message = stringResource(R.string.delete_data_confirm_format, displayName),
            onDismiss = { itemToDelete = null },
            onConfirm = {
                manageDataViewModel.deleteManageDataSingle(targetItem.location.id)
                itemToDelete = null
            }
        )
    }
}
