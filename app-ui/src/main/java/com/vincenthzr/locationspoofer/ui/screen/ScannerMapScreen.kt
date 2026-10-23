package com.vincenthzr.locationspoofer.ui.screen

import com.vincenthzr.locationspoofer.ui.theme.AppMotion
import com.vincenthzr.locationspoofer.ui.screen.tabs.location.MapControlButton
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import com.vincenthzr.locationspoofer.ui.components.CollectionRouteMapHelper
import com.vincenthzr.locationspoofer.data.db.CollectionRouteRecord
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.AppMapView
import com.vincenthzr.locationspoofer.ui.components.MapTypeDialog
import com.vincenthzr.locationspoofer.ui.components.MapCoverageHelper
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.fetchCurrentLocation
import com.vincenthzr.locationspoofer.viewmodel.isDomesticEnvironment
import com.vincenthzr.locationspoofer.viewmodel.setMapEngine
import com.vincenthzr.locationspoofer.viewmodel.setMapType
import com.vincenthzr.locationspoofer.ui.components.AppBackButton

@Composable
fun ScannerMapScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    var showMapTypeDialog by remember { mutableStateOf(false) }
    val environmentLocations by viewModel.environmentLocations.collectAsState()
    val collectionRoutes by viewModel.collectionRoutes.collectAsState()
    val locations = remember(environmentLocations) { environmentLocations.map { it.location }.filter { it.collectionRouteId == null } }

    // 进入页面时主动尝试刷新一次真实 GPS 定位
    LaunchedEffect(Unit) {
        viewModel.fetchCurrentLocation(context) { lat, lng ->
            mapController?.animateCamera(lat, lng, 16.5f)
        }
    }

    // 同步地图类型
    LaunchedEffect(mapController, uiState.mapType) {
        mapController?.setMapType(uiState.mapType)
    }

    // 数据变化时刷新覆盖范围，包括数量不变的坐标编辑和重复采集。
    LaunchedEffect(mapController, locations, collectionRoutes, uiState.collectionRoutePoints) {
        val controller = mapController ?: return@LaunchedEffect
        val currentLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
        val currentLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
        controller.clear()

        // 绘制覆盖范围圆圈（带空间降采样与硬上限，确保高帧率流畅运行）
        MapCoverageHelper.drawCoverage(controller, locations, currentLat, currentLng)

        collectionRoutes.take(10).forEach { CollectionRouteMapHelper.draw(controller, it.route, 12) }
        if (uiState.collectionRoutePoints.size >= 2) {
            CollectionRouteMapHelper.draw(controller, CollectionRouteRecord(
                pointsJson = kotlinx.serialization.json.Json.encodeToString(uiState.collectionRoutePoints)), 60)
        } else uiState.collectionRoutePoints.firstOrNull()?.let {
            controller.addMarker(it.lat, it.lng, "", com.vincenthzr.locationspoofer.ui.components.MarkerType.GREEN)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppMapView(
            mapEngine = uiState.mapEngine,
            isDomestic = viewModel.isDomesticEnvironment(),
            modifier = Modifier.fillMaxSize(),
            onMapReady = { controller ->
                mapController = controller
                controller.disableUiControls()
                // 地图加载就绪后立即聚焦到当前位置
                val currentLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                val currentLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                controller.moveCamera(currentLat, currentLng, 16.5f)
            }
        )

        // 顶部操作栏（左侧圆形返回胶囊，右侧扫描状态胶囊）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 独立立体圆形返回按钮（替换原先的 x 图标）
            AppBackButton(onClick = onClose)

            // 状态指示：M3 Assist Chip 风格的浮动标签（elevated）
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.isContinuousScanning) Icons.Rounded.Radar else Icons.Rounded.PauseCircle,
                        contentDescription = null,
                        tint = if (uiState.isContinuousScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isStoppingCollection) {
                            stringResource(R.string.collection_stopping)
                        } else if (uiState.isContinuousScanning) {
                            stringResource(
                                R.string.scanning_status_active,
                                uiState.environmentRecordCount
                            )
                        } else {
                            stringResource(R.string.scanning_status_inactive)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 右上角实时扫描统计卡片 (放在状态胶囊下方)
        AnimatedVisibility(
            visible = uiState.isContinuousScanning || uiState.scannedWifiCount > 0 || uiState.scannedCellCount > 0 || uiState.scannedBluetoothCount > 0,
            enter = fadeIn(AppMotion.defaultEffectsSpec()),
            exit = fadeOut(AppMotion.fastEffectsSpec()),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(
                            R.string.scanned_wifi_count,
                            uiState.scannedWifiCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.scanned_cell_count,
                            uiState.scannedCellCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.scanned_bt_count,
                            uiState.scannedBluetoothCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 右侧悬浮功能按钮（地图图层切换）
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            // 图层切换按钮
            MapControlButton(
                icon = Icons.Rounded.Layers,
                onClick = { showMapTypeDialog = true },
                contentDescription = stringResource(R.string.map_layers)
            )
        }

        if (uiState.isDrawingCollectionRoute) {
            Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.collection_route_center),
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        CollectionRouteControls(viewModel, uiState, mapController,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
    }

    if (showMapTypeDialog) {
        MapTypeDialog(
            currentMapType = uiState.mapType,
            onMapTypeSelected = { viewModel.setMapType(it) },
            currentMapEngine = uiState.mapEngine,
            onMapEngineSelected = { viewModel.setMapEngine(it) },
            onDismiss = { showMapTypeDialog = false }
        )
    }
}
