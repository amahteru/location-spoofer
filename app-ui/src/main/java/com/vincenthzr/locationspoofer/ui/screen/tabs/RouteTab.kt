package com.vincenthzr.locationspoofer.ui.screen.tabs

import androidx.core.graphics.toColorInt
import com.vincenthzr.locationspoofer.ui.theme.AppMotion
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.*
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.AppMapMarker
import com.vincenthzr.locationspoofer.ui.components.MapTypeDialog
import com.vincenthzr.locationspoofer.ui.components.MarkerType
import com.vincenthzr.locationspoofer.ui.screen.AppPoiItem
import com.vincenthzr.locationspoofer.ui.screen.HomeSearchBar
import com.vincenthzr.locationspoofer.ui.screen.performPoiSearch
import com.vincenthzr.locationspoofer.ui.screen.tabs.route.*
import com.vincenthzr.locationspoofer.ui.components.MapCoverageHelper
import com.vincenthzr.locationspoofer.viewmodel.prepareRouteSimulationOptions
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.setAltitude
import com.vincenthzr.locationspoofer.viewmodel.setAltitudeVariation
import com.vincenthzr.locationspoofer.viewmodel.ManageDataViewModel
import com.vincenthzr.locationspoofer.viewmodel.addRoutePoint
import com.vincenthzr.locationspoofer.viewmodel.fetchCurrentLocation
import com.vincenthzr.locationspoofer.viewmodel.finishSelectingPoints
import com.vincenthzr.locationspoofer.viewmodel.isDomesticEnvironment
import com.vincenthzr.locationspoofer.viewmodel.performLocalSearch
import com.vincenthzr.locationspoofer.viewmodel.restartSelectingPoints
import com.vincenthzr.locationspoofer.viewmodel.setCustomSpeedMs
import com.vincenthzr.locationspoofer.viewmodel.setMapEngine
import com.vincenthzr.locationspoofer.viewmodel.setMapType
import com.vincenthzr.locationspoofer.viewmodel.setRouteRunMode
import com.vincenthzr.locationspoofer.viewmodel.setRouteSimMode
import com.vincenthzr.locationspoofer.viewmodel.setSatelliteCount
import com.vincenthzr.locationspoofer.viewmodel.setSearchMode
import com.vincenthzr.locationspoofer.viewmodel.setStopAtDestination
import com.vincenthzr.locationspoofer.viewmodel.setUseRealRoute
import com.vincenthzr.locationspoofer.viewmodel.startRoutePlanning
import com.vincenthzr.locationspoofer.viewmodel.stopRoutePlanning
import com.vincenthzr.locationspoofer.viewmodel.toggleEnableJitter
import com.vincenthzr.locationspoofer.viewmodel.toggleMockBluetooth
import com.vincenthzr.locationspoofer.viewmodel.toggleMockCell
import com.vincenthzr.locationspoofer.viewmodel.toggleMockWifi
import com.vincenthzr.locationspoofer.viewmodel.undoLastRoutePoint
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.vincenthzr.locationspoofer.ui.theme.warning
import androidx.compose.foundation.clickable
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@OptIn(ExperimentalSharedTransitionApi::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun RouteTab(
    viewModel: MainViewModel,
    uiState: AppState,
    mapController: AppMapController?,
    isActive: Boolean = true,
    bottomBarHeight: Dp = 90.dp,
    manageDataViewModel: ManageDataViewModel = koinViewModel()
) {
    val motion = AppMotion
    val collectionRoutes by viewModel.collectionRoutes.collectAsState()
    val context = LocalContext.current
    var showMapTypeDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var showSavedRoutesDialog by remember { mutableStateOf(false) }
    val isDomestic = viewModel.isDomesticEnvironment()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppPoiItem>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchBounds by remember { mutableStateOf(Rect.Zero) }
    var searchResultBounds by remember { mutableStateOf(Rect.Zero) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    var bottomActionHeightPx by remember { mutableIntStateOf(0) }

    val stage = uiState.routePlanStage
    val isRunning = stage == RoutePlanStage.RUNNING
    val isManual = uiState.routeRunMode == RouteRunMode.MANUAL
    val routePoints = uiState.routePoints

    val submitSearch: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (uiState.searchMode == SearchMode.LOCAL) {
            coroutineScope.launch {
                val results = viewModel.performLocalSearch()
                searchResults = results
                showSearchResults = results.isNotEmpty()
                if (results.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.no_matching_local_data), Toast.LENGTH_SHORT).show()
                }
            }
        } else if (searchQuery.isNotBlank()) {
            performPoiSearch(
                context = context,
                mapEngine = uiState.mapEngine,
                keyword = searchQuery,
                isDomestic = isDomestic
            ) { r ->
                searchResults = r
                showSearchResults = r.isNotEmpty()
                if (r.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.no_search_results), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val exitSearch: () -> Unit = {
        isSearchActive = false
        showSearchResults = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    BackHandler(enabled = isSearchActive || showSearchResults, onBack = exitSearch)

    val density = LocalDensity.current
    val bottomActionHeightDp = with(density) { bottomActionHeightPx.toDp() }

    LaunchedEffect(mapController, uiState.mapType, isActive) {
        if (!isActive) return@LaunchedEffect
        mapController?.setMapType(uiState.mapType)
    }

    val manageDataList = manageDataViewModel.uiState.collectAsState().value.dataList
    var liveMarker by remember { mutableStateOf<AppMapMarker?>(null) }
    LaunchedEffect(routePoints, mapController, manageDataList, isActive, collectionRoutes, uiState.selectedCollectionRouteId) {
        if (!isActive) return@LaunchedEffect
        val map = mapController ?: return@LaunchedEffect
        map.clear()
        collectionRoutes.firstOrNull { it.route.id == uiState.selectedCollectionRouteId }?.let {
            com.vincenthzr.locationspoofer.ui.components.CollectionRouteMapHelper.draw(map, it.route)
        }
        liveMarker = null
        val locations = manageDataList.map { it.location }
        MapCoverageHelper.drawCoverage(map, locations)

        if (routePoints.size >= 2) {
            map.addPolyline(
                routePoints.map { Pair(it.lat, it.lng) },
                "#FF388BFD".toColorInt(),
                8f
            )
        }
        routePoints.forEachIndexed { idx, p ->
            val type = when {
                idx == 0 -> MarkerType.GREEN
                idx == routePoints.lastIndex && routePoints.size > 1 -> MarkerType.RED
                else -> MarkerType.DEFAULT
            }
            if (uiState.useRealRoute && uiState.routePlanStage == RoutePlanStage.RUNNING && type == MarkerType.DEFAULT) {
                return@forEachIndexed
            }
            val startBadge = context.getString(R.string.route_start_badge)
            val endBadge = context.getString(R.string.route_end_badge)
            val label = when (type) {
                MarkerType.GREEN -> startBadge
                MarkerType.RED -> endBadge
                else -> "${idx + 1}"
            }
            map.addMarker(
                p.lat,
                p.lng,
                if (type == MarkerType.RED && uiState.useRealRoute && uiState.routePlanStage == RoutePlanStage.RUNNING) endBadge else label,
                type
            )
        }

        if (uiState.isSpoofingActive) {
            val currentLat = uiState.latitudeInput.toDoubleOrNull()
            val currentLng = uiState.longitudeInput.toDoubleOrNull()
            if (currentLat != null && currentLng != null) {
                liveMarker = map.addMarker(
                    currentLat, currentLng,
                    context.getString(R.string.current_location),
                    MarkerType.ORANGE
                )
            }
        }
    }

    val lat = uiState.latitudeInput.toDoubleOrNull()
    val lng = uiState.longitudeInput.toDoubleOrNull()
    LaunchedEffect(lat, lng, uiState.isSpoofingActive, uiState.routePlanStage) {
        if (uiState.isSpoofingActive && lat != null && lng != null) {
            if (uiState.routePlanStage != RoutePlanStage.RUNNING) {
                mapController?.animateCamera(lat, lng)
            }
            if (liveMarker != null) {
                liveMarker?.setPosition(lat, lng)
            } else {
                liveMarker = mapController?.addMarker(
                    lat, lng,
                    context.getString(R.string.current_location),
                    MarkerType.ORANGE
                )
            }
        }
    }

    LaunchedEffect(uiState.routePlanStage, routePoints, isActive, mapController, uiState.selectedCollectionRouteId) {
        if (!isActive) return@LaunchedEffect
        if ((uiState.routePlanStage == RoutePlanStage.RUNNING ||
                (uiState.routePlanStage == RoutePlanStage.READY && uiState.selectedCollectionRouteId != null)) && routePoints.size >= 2) {
            val padLeft = with(density) { 36.dp.roundToPx() }
            val padTop = with(density) { 80.dp.roundToPx() }
            val padRight = with(density) { 36.dp.roundToPx() }
            val effectiveBottomDp =
                if (bottomActionHeightDp > 0.dp) bottomActionHeightDp + bottomBarHeight + 24.dp else bottomBarHeight + 160.dp
            val padBottom =
                with(density) { effectiveBottomDp.roundToPx() }
            mapController?.fitBounds(
                points = routePoints.map { Pair(it.lat, it.lng) },
                paddingLeft = padLeft,
                paddingTop = padTop,
                paddingRight = padRight,
                paddingBottom = padBottom
            )
        }
    }

    SharedTransitionLayout {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None
            },
            label = "route_search_transition"
        ) content@{ searchActive ->
            val searchModifier = Modifier
                .sharedElement(
                    sharedContentState = rememberSharedContentState(key = "route_search_bar"),
                    animatedVisibilityScope = this@content,
                    boundsTransform = { initialBounds, targetBounds ->
                        val isTopSearchTransition = (initialBounds.top < 350f && targetBounds.top > 350f) ||
                                (initialBounds.top > 350f && targetBounds.top < 350f)
                        if (isTopSearchTransition) {
                            motion.defaultSpatialSpec()
                        } else {
                            snap()
                        }
                    }
                )
                .onGloballyPositioned { searchBounds = it.boundsInRoot() }

            Box(modifier = Modifier.fillMaxSize()) {
                // 搜索激活且显示联想结果时：点击外部遮罩退出联想列表
                if (searchActive && showSearchResults) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(searchBounds, searchResultBounds) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val up = waitForUpOrCancellation()
                                    if (up != null &&
                                        (up.position - down.position).getDistance() < viewConfiguration.touchSlop &&
                                        !searchBounds.contains(up.position) &&
                                        !searchResultBounds.contains(up.position)
                                    ) {
                                        showSearchResults = false
                                    }
                                }
                            }
                    )
                }

                // 中心瞄准十字准心（选点阶段显示，搜索激活时隐藏）
                if ((stage == RoutePlanStage.SELECTING || stage == RoutePlanStage.IDLE) && !searchActive) {
                    Icon(
                        Icons.Rounded.AddLocationAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .padding(bottom = AppSpacing.lg)
                    )
                }

                if (isRunning && isManual) {
                    Icon(
                        Icons.Rounded.PersonPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.warning,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .padding(bottom = AppSpacing.lg)
                    )
                }

                // 顶部操作卡片：包含路径点统计与撤销操作（搜索激活时隐藏，避免与顶部搜索栏发生遮挡）
                if (routePoints.isNotEmpty() && (stage == RoutePlanStage.IDLE || stage == RoutePlanStage.SELECTING) && !searchActive) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = AppSpacing.md)
                            .animateContentSize(motion.defaultSpatialSpec()),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.xs, top = AppSpacing.xs, bottom = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                        ) {
                            Text(
                                stringResource(R.string.selected_waypoints_count, routePoints.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            VerticalDivider(modifier = Modifier.height(24.dp))

                            // 撤销上一个点
                            IconButton(onClick = { viewModel.undoLastRoutePoint() }) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.undo))
                            }

                            // 全部清除 / 重新选点
                            IconButton(onClick = { viewModel.restartSelectingPoints() }) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.reselect))
                            }
                        }
                    }
                }

                // 右侧悬浮地图控制按钮组
                val animatedBottomPadding by animateDpAsState(
                    targetValue = bottomBarHeight + bottomActionHeightDp + 16.dp,
                    animationSpec = motion.defaultSpatialSpec(),
                    label = "fab_padding"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = AppSpacing.lg, bottom = animatedBottomPadding)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RouteControlButton(
                            icon = Icons.Rounded.Layers,
                            onClick = { showMapTypeDialog = true }
                        )
                        RouteControlButton(
                            icon = Icons.Rounded.Bookmarks,
                            onClick = { showSavedRoutesDialog = true }
                        )
                        RouteControlButton(
                            icon = Icons.Rounded.MyLocation,
                            onClick = {
                                viewModel.fetchCurrentLocation(context) { lat, lng ->
                                    mapController?.animateCamera(lat, lng, 16f)
                                }
                            }
                        )
                    }
                }

                // 底部多状态操作面板
                RouteBottomPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomBarHeight + AppSpacing.sm, start = AppSpacing.lg, end = AppSpacing.lg)
                        .onGloballyPositioned { coordinates ->
                            bottomActionHeightPx = coordinates.size.height
                        },
                    stage = stage,
                    routePoints = routePoints,
                    uiState = uiState,
                    onConfirmPoint = {
                        val tLat = mapController?.cameraTargetLat
                        val tLng = mapController?.cameraTargetLng
                        if (tLat != null && tLng != null) {
                            viewModel.addRoutePoint(tLat, tLng)
                        } else {
                            val fallbackLat = uiState.latitudeInput.toDoubleOrNull()
                            val fallbackLng = uiState.longitudeInput.toDoubleOrNull()
                            if (fallbackLat != null && fallbackLng != null) {
                                viewModel.addRoutePoint(fallbackLat, fallbackLng)
                            }
                        }
                    },
                    onFinishSelecting = {
                        viewModel.finishSelectingPoints()
                    },
                    onRestartSelecting = {
                        viewModel.restartSelectingPoints()
                    },
                    onSaveRoute = { showSaveRouteDialog = true },
                    onStartPlanning = { viewModel.prepareRouteSimulationOptions { showConfigDialog = true } },
                    onStopRoute = {
                        viewModel.stopRoutePlanning()
                    },
                    searchBar = if (stage == RoutePlanStage.IDLE || stage == RoutePlanStage.SELECTING) {
                        { barModifier ->
                            if (!searchActive) {
                                HomeSearchBar(
                                    query = searchQuery,
                                    searchMode = uiState.searchMode,
                                    onSearchModeChange = viewModel::setSearchMode,
                                    onQueryChange = { searchQuery = it },
                                    onSearch = submitSearch,
                                    onFocus = { isSearchActive = true },
                                    modifier = searchModifier.then(barModifier),
                                    focusRequester = searchFocusRequester
                                )
                            } else {
                                Spacer(modifier = barModifier.height(56.dp))
                            }
                        }
                    } else null
                )

                // 搜索激活时：顶部搜索栏与联想结果卡片
                if (searchActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = AppSpacing.sm)
                    ) {
                        HomeSearchBar(
                            query = searchQuery,
                            searchMode = uiState.searchMode,
                            onSearchModeChange = viewModel::setSearchMode,
                            onQueryChange = { searchQuery = it },
                            onSearch = submitSearch,
                            onFocus = { isSearchActive = true },
                            onBack = exitSearch,
                            modifier = searchModifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.lg),
                            focusRequester = searchFocusRequester
                        )

                        AnimatedVisibility(
                            visible = showSearchResults && searchResults.isNotEmpty(),
                            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec())
                        ) {
                            ElevatedCard(
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                                    .onGloballyPositioned { searchResultBounds = it.boundsInRoot() }
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                    items(searchResults) { poi ->
                                        ListItem(
                                            modifier = Modifier.clickable {
                                                mapController?.animateCamera(poi.lat, poi.lng, 17f)
                                                isSearchActive = false
                                                showSearchResults = false
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            },
                                            leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
                                            headlineContent = { Text(poi.title) },
                                            supportingContent = if (poi.snippet.isNotBlank()) {
                                                { Text(poi.snippet) }
                                            } else null,
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        RouteConfigDialog(
            uiState = uiState,
            onDismiss = {
                showConfigDialog = false
            },
            onStartRoute = {
                showConfigDialog = false
                viewModel.startRoutePlanning()
            },
            onRunModeChange = viewModel::setRouteRunMode,
            onSpeedChange = viewModel::setRouteSimMode,
            onCustomSpeedChange = viewModel::setCustomSpeedMs,
            onUseRealRouteChange = viewModel::setUseRealRoute,
            onStopAtDestinationChange = viewModel::setStopAtDestination,
            onToggleWifi = viewModel::toggleMockWifi,
            onToggleCell = viewModel::toggleMockCell,
            onToggleBluetooth = viewModel::toggleMockBluetooth,
            onToggleJitter = viewModel::toggleEnableJitter,
            onAltitudeChange = viewModel::setAltitude,
            onAltitudeVariationChange = viewModel::setAltitudeVariation,
            onSatelliteCountChange = viewModel::setSatelliteCount
        )
    }

    if (uiState.isFetchingRoute) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(AppSpacing.xxl)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.planning_route_trajectory),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
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

    if (showSaveRouteDialog) {
        SaveRouteDialog(
            routePoints = routePoints,
            viewModel = viewModel,
            onDismiss = { showSaveRouteDialog = false }
        )
    }

    if (showSavedRoutesDialog) {
        SavedRoutesDialog(
            uiState = uiState,
            viewModel = viewModel,
            mapController = mapController,
            bottomActionHeightDp = bottomActionHeightDp,
            bottomBarHeight = bottomBarHeight,
            onDismiss = { showSavedRoutesDialog = false }
        )
    }
}
