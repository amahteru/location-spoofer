package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.theme.animateListItem
import io.github.amahteru.locationspoofer.ui.components.LabelTone
import io.github.amahteru.locationspoofer.ui.components.StatusLabel
import io.github.amahteru.locationspoofer.ui.components.AppTopBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.AppMapView
import io.github.amahteru.locationspoofer.ui.components.CollectedDataMenu
import io.github.amahteru.locationspoofer.ui.components.CollectedLocationCard
import io.github.amahteru.locationspoofer.ui.components.CollectedRouteCard
import io.github.amahteru.locationspoofer.ui.screen.managedata.ModernEditDataDialog
import io.github.amahteru.locationspoofer.ui.screen.managedata.ManageDeleteConfirmDialog
import io.github.amahteru.locationspoofer.ui.components.MapCoverageHelper
import io.github.amahteru.locationspoofer.ui.components.rememberMapPalette
import io.github.amahteru.locationspoofer.ui.components.STREET_ZOOM
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.ManageDataViewModel
import io.github.amahteru.locationspoofer.data.model.geometry
import io.github.amahteru.locationspoofer.viewmodel.selectCollectedRoute
import io.github.amahteru.locationspoofer.viewmodel.saveCollectionRouteInfo
import io.github.amahteru.locationspoofer.viewmodel.deleteCollectedRoute
import io.github.amahteru.locationspoofer.viewmodel.selectCollectedLocation
import io.github.amahteru.locationspoofer.viewmodel.syncFavoriteCoordinateIfExists
import io.github.amahteru.locationspoofer.viewmodel.toggleCollectedLocationFavorite
import io.github.amahteru.locationspoofer.viewmodel.toggleCollectedRouteFavorite
import io.github.amahteru.locationspoofer.data.model.routePoints
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun ManageDataScreen(
    viewModel: MainViewModel,
    uiState: io.github.amahteru.locationspoofer.data.model.AppState,
    onClose: () -> Unit,
    manageDataViewModel: ManageDataViewModel = koinViewModel()
) {
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    var editingItem by remember { mutableStateOf<CompleteLocation?>(null) }
    var itemToDelete by remember { mutableStateOf<CompleteLocation?>(null) }

    val manageDataUiState by manageDataViewModel.uiState.collectAsState()
    val dataList = manageDataUiState.dataList
    val collectionRoutes = manageDataUiState.collectionRoutes
    var editingRoute by remember { mutableStateOf<io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord?>(null) }
    var deletingRoute by remember { mutableStateOf<io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord?>(null) }

    val favoritedCoords = remember(uiState.savedLocations) {
        uiState.savedLocations.mapTo(HashSet()) { it.lat to it.lng }
    }

    val favoritedRoutes = remember(uiState.savedRoutes) { uiState.savedRoutes.mapTo(HashSet()) { it.points } }

    BackHandler(onBack = onClose)

    LaunchedEffect(mapController, uiState.mapType) {
        mapController?.setMapType(uiState.mapType)
    }

    val palette = rememberMapPalette()
    LaunchedEffect(mapController, dataList, collectionRoutes, palette) {
        val controller = mapController ?: return@LaunchedEffect
        controller.clear()
        val locations = dataList.map { it.location }
        val last = locations.lastOrNull()
        MapCoverageHelper.drawCoverage(controller, locations, palette, last?.lat, last?.lng)
        collectionRoutes.take(20).forEach { route ->
            io.github.amahteru.locationspoofer.ui.components.CollectionRouteMapHelper.draw(controller, route.route, palette, maxOf(8, 160 / maxOf(1, collectionRoutes.size)))
        }
        if (collectionRoutes.isNotEmpty()) {
            val geometry = collectionRoutes.first().route.geometry()
            geometry.firstOrNull()?.let { controller.moveCamera(it.lat, it.lng, STREET_ZOOM) }
            delay(300)
            controller.fitBounds(geometry.map { it.lat to it.lng }, 80, 200, 80, 80)
        } else if (last != null) {
            controller.moveCamera(last.lat, last.lng, STREET_ZOOM)
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AppSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
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
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.36f)
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                    shape = MaterialTheme.shapes.large
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppMapView(
                            mapEngine = uiState.mapEngine,
                            modifier = Modifier.fillMaxSize(),
                            onMapReady = { controller ->
                                mapController = controller
                                controller.disableUiControls()
                            }
                        )

                        StatusLabel(
                            text = stringResource(R.string.local_collection_records_count, dataList.size, collectionRoutes.size),
                            tone = LabelTone.Secondary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(AppSpacing.md)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.64f),
                    contentPadding = PaddingValues(
                        start = AppSpacing.lg,
                        end = AppSpacing.lg,
                        top = AppSpacing.sm,
                        bottom = AppSpacing.xl
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    items(collectionRoutes, key = { "collection-route-${it.route.id}" }) { route ->
                        Box(Modifier.animateListItem(this)) {
                            CollectedRouteCard(
                                route,
                                onClick = {
                                    viewModel.selectCollectedRoute(route.route.id)
                                    mapController?.let { io.github.amahteru.locationspoofer.ui.components.CollectionRouteMapHelper.show(it, route.route, palette, 120) }
                                }
                            ) {
                                CollectedDataMenu(
                                    onEdit = { editingRoute = route.route },
                                    onDelete = { deletingRoute = route.route },
                                    favorited = route.route.routePoints() in favoritedRoutes,
                                    onFavorite = { viewModel.toggleCollectedRouteFavorite(route.route) }
                                )
                            }
                        }
                    }
                    items(dataList, key = { it.location.id }) { item ->
                        Box(Modifier.animateListItem(this)) {
                            CollectedLocationCard(
                                item,
                                onClick = {
                                    viewModel.selectCollectedLocation(item.location.id, announce = true)
                                    mapController?.animateCamera(
                                        item.location.lat,
                                        item.location.lng,
                                        STREET_ZOOM
                                    )
                                }
                            ) {
                                CollectedDataMenu(
                                    onEdit = { editingItem = item },
                                    onDelete = { itemToDelete = item },
                                    favorited = (item.location.lat to item.location.lng) in favoritedCoords,
                                    onFavorite = { viewModel.toggleCollectedLocationFavorite(item.location.id) }
                                )
                            }
                        }
                    }
                }

            }
        }
    }

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
                viewModel.syncFavoriteCoordinateIfExists(currentItem.location.lat, currentItem.location.lng, lat, lng)
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
