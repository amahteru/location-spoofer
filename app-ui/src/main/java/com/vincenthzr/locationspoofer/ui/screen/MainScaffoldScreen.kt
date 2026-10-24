package com.vincenthzr.locationspoofer.ui.screen

import com.vincenthzr.locationspoofer.ui.theme.AppMotion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.SubPageOverlay
import com.vincenthzr.locationspoofer.ui.components.AppMapView
import com.vincenthzr.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.UpdateViewModel
import com.vincenthzr.locationspoofer.viewmodel.getIgnoredVersion
import com.vincenthzr.locationspoofer.viewmodel.handleSpoofingIntent
import com.vincenthzr.locationspoofer.viewmodel.isDomesticEnvironment
import com.vincenthzr.locationspoofer.viewmodel.setIgnoredVersion
import org.koin.androidx.compose.koinViewModel

enum class BottomTab(val icon: ImageVector) {
    Location(Icons.Rounded.MyLocation),
    Route(Icons.Rounded.Route),
    Features(Icons.Rounded.Extension),
    Info(Icons.Rounded.Settings);

    @Composable
    fun getLabel(): String {
        return when (this) {
            Location -> stringResource(R.string.tab_location)
            Route -> stringResource(R.string.tab_route)
            Features -> stringResource(R.string.tab_features)
            Info -> stringResource(R.string.tab_info)
        }
    }
}

enum class MainSubScreen {
    None,
    CoordinateConfig,
    ScannerMap,
    ManageData,
    Update,
    LanguageSettings,
    MapEngineSettings,
    SignatureAuth,
    RootDiagnostics,
    VendorScheme,
    MotionRealism,
    BackgroundKeepAlive,
    EnvTokens,
    SystemHookApps
}

@Composable
fun MainScaffoldScreen(
    viewModel: MainViewModel,
    uiState: AppState
) {
    val pageCount = BottomTab.values().size
    var selectedTab by remember { mutableIntStateOf(BottomTab.Location.ordinal) }
    var currentSubScreen by remember { mutableStateOf(MainSubScreen.None) }
    var activeSubScreen by remember { mutableStateOf(MainSubScreen.None) }
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val updateViewModel: UpdateViewModel = koinViewModel()

    val updateUiState by updateViewModel.uiState.collectAsState()
    var hasAutoCheckedUpdates by remember { mutableStateOf(false) }
    var showStartupUpdateDialog by remember { mutableStateOf(false) }
    val targetReleases = remember(updateUiState.releases, uiState.checkBetaUpdates) {
        if (uiState.checkBetaUpdates) updateUiState.releases else updateUiState.releases.filter { !it.isPrerelease }
    }
    val latestRelease = targetReleases.firstOrNull()

    // 软件启动进入主界面时，立即在后台检索是否有新版本发布
    LaunchedEffect(Unit) {
        updateViewModel.fetchReleases()
    }

    // 检测到未被忽略的新版本时弹出启动更新提示框
    LaunchedEffect(targetReleases, updateUiState.isLoading) {
        if (!hasAutoCheckedUpdates && !updateUiState.isLoading && targetReleases.isNotEmpty()) {
            if (latestRelease != null) {
                val latestVersion = latestRelease.versionName
                val currentVersion = BuildConfig.VERSION_NAME
                val ignoredVersion = viewModel.getIgnoredVersion()
                if (isNewerVersion(
                        latestVersion,
                        currentVersion
                    ) && latestVersion != ignoredVersion
                ) {
                    showStartupUpdateDialog = true
                }
            }
            hasAutoCheckedUpdates = true
        }
    }

    LaunchedEffect(currentSubScreen) {
        if (currentSubScreen != MainSubScreen.None) {
            activeSubScreen = currentSubScreen
        }
    }

    // 针对非首页 Tab（路线/功能/信息）的系统返回拦截：返回到定位首页
    BackHandler(enabled = currentSubScreen == MainSubScreen.None && selectedTab != BottomTab.Location.ordinal) {
        selectedTab = BottomTab.Location.ordinal
    }

    LaunchedEffect(selectedTab, mapController, uiState.mapType) {
        if (selectedTab == 0) {
            mapController?.clear()
            mapController?.setMapType(uiState.mapType)
        }
    }

    // M3 标准动效：位移 / 尺寸用 spatial 弹簧，透明度 / 缩放等非位移效果用 effects 弹簧
    val motion = AppMotion

    val selectPage: (Int) -> Unit = { index ->
        selectedTab = index.coerceIn(0, pageCount - 1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(
                    visible = currentSubScreen == MainSubScreen.None,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec())
                ) {
                    NavigationBar {
                        BottomTab.values().forEachIndexed { index, tab ->
                            val label = tab.getLabel()
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectPage(index) },
                                icon = { Icon(imageVector = tab.icon, contentDescription = label) },
                                label = { Text(text = label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 底层地图层（定位与路线 Tab 共享）
                Box(modifier = Modifier.fillMaxSize()) {
                    AppMapView(
                        modifier = Modifier.fillMaxSize(),
                        mapEngine = uiState.mapEngine,
                        isDomestic = viewModel.isDomesticEnvironment(),
                        onMapReady = { controller ->
                            mapController = controller
                            controller.disableUiControls()
                            controller.setMapType(uiState.mapType)
                            val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                            val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                            controller.moveCamera(initLat, initLng, 15f)

                            controller.setOnCameraChangeListener { lat, lng ->
                                if (currentSelectedTab == BottomTab.Location.ordinal) {
                                    viewModel.handleSpoofingIntent(
                                        SpoofingIntent.ConfirmMapPoint(
                                            lat,
                                            lng
                                        )
                                    )
                                }
                            }
                            controller.setOnCameraMoveListener { lat, lng ->
                                if (currentSelectedTab == BottomTab.Location.ordinal) {
                                    viewModel.handleSpoofingIntent(
                                        SpoofingIntent.MapPointMoved(
                                            lat,
                                            lng
                                        )
                                    )
                                }
                            }
                        }
                    )

                    // 定位选点十字准心（仅在定位 Tab 显示）
                    AnimatedVisibility(
                        visible = selectedTab == 0,
                        modifier = Modifier.align(Alignment.Center),
                        enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultEffectsSpec(), initialScale = 0.8f),
                        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastEffectsSpec(), targetScale = 0.8f)
                    ) {
                        Icon(
                            Icons.Rounded.AddLocationAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(36.dp)
                                .padding(bottom = 16.dp)
                        )
                    }
                }

                // 底部 Tab 内容切换：整页左右滑动，按 Tab 顺序决定方向
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        motion.pageSlideTransition(if (targetState > initialState) 1 else -1)
                    },
                    label = "main_tabs_transition"
                ) { targetTab ->
                    when (targetTab) {
                        BottomTab.Location.ordinal -> com.vincenthzr.locationspoofer.ui.screen.tabs.LocationTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            mapController = mapController,
                            onCollectionRouteSelected = { selectedTab = BottomTab.Route.ordinal },
                            tabBarHeight = paddingValues.calculateBottomPadding()
                        )

                        BottomTab.Route.ordinal -> com.vincenthzr.locationspoofer.ui.screen.tabs.RouteTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            mapController = mapController,
                            isActive = true,
                            bottomBarHeight = paddingValues.calculateBottomPadding()
                        )

                        BottomTab.Features.ordinal -> com.vincenthzr.locationspoofer.ui.screen.tabs.FeaturesTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            tabBarHeight = paddingValues.calculateBottomPadding(),
                            onNavigateToCoordinate = {
                                currentSubScreen = MainSubScreen.CoordinateConfig
                            },
                            onNavigateToScanner = { currentSubScreen = MainSubScreen.ScannerMap },
                            onNavigateToManageData = { currentSubScreen = MainSubScreen.ManageData },
                            onNavigateToSystemHookApps = { currentSubScreen = MainSubScreen.SystemHookApps }
                        )

                        BottomTab.Info.ordinal -> com.vincenthzr.locationspoofer.ui.screen.tabs.InfoTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            updateUiState = updateUiState,
                            tabBarHeight = paddingValues.calculateBottomPadding(),
                            onNavigateToUpdate = { currentSubScreen = MainSubScreen.Update },
                            onNavigateToLanguage = { currentSubScreen = MainSubScreen.LanguageSettings },
                            onNavigateToMapEngine = { currentSubScreen = MainSubScreen.MapEngineSettings },
                            onNavigateToSignatureAuth = { currentSubScreen = MainSubScreen.SignatureAuth },
                            onNavigateToRootDiagnostics = { currentSubScreen = MainSubScreen.RootDiagnostics },
                            onNavigateToVendorScheme = { currentSubScreen = MainSubScreen.VendorScheme },
                            onNavigateToMotionRealism = { currentSubScreen = MainSubScreen.MotionRealism },
                            onNavigateToBackgroundKeepAlive = { currentSubScreen = MainSubScreen.BackgroundKeepAlive },
                            onNavigateToEnvTokens = { currentSubScreen = MainSubScreen.EnvTokens }
                        )
                    }
                }
            }
        }

        // 全屏子页面覆盖层：从右侧整页滑入，返回时向右滑出
        SubPageOverlay(visible = currentSubScreen != MainSubScreen.None) {
            val subScreenToRender =
                if (currentSubScreen != MainSubScreen.None) currentSubScreen else activeSubScreen
            Box(modifier = Modifier.fillMaxSize()) {
                when (subScreenToRender) {
                    MainSubScreen.CoordinateConfig -> AppCoordinateScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onBack = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.ScannerMap -> ScannerMapScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.ManageData -> ManageDataScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.Update -> UpdateScreen(
                        updateViewModel = updateViewModel,
                        viewModel = viewModel,
                        onBack = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.LanguageSettings -> com.vincenthzr.locationspoofer.ui.screen.settings.LanguageSettingsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.MapEngineSettings -> com.vincenthzr.locationspoofer.ui.screen.settings.MapEngineSettingsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.SignatureAuth -> com.vincenthzr.locationspoofer.ui.screen.settings.SignatureAuthScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.RootDiagnostics -> com.vincenthzr.locationspoofer.ui.screen.settings.RootDiagnosticsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.MotionRealism -> com.vincenthzr.locationspoofer.ui.screen.settings.MotionRealismScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.VendorScheme -> com.vincenthzr.locationspoofer.ui.screen.settings.VendorSchemeScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.BackgroundKeepAlive -> com.vincenthzr.locationspoofer.ui.screen.settings.BackgroundKeepAliveScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.EnvTokens -> com.vincenthzr.locationspoofer.ui.screen.settings.EnvTokensScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.SystemHookApps -> SystemHookAppsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onBack = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.None -> Unit
                }
            }
        }

        // 启动时若检测到新版本，弹出更新弹窗（支持忽略此版本、前往更新）
        if (showStartupUpdateDialog && latestRelease != null) {
            StartupUpdateDialog(
                latestRelease = latestRelease,
                onDismiss = { showStartupUpdateDialog = false },
                onNavigateToUpdate = {
                    showStartupUpdateDialog = false
                    currentSubScreen = MainSubScreen.Update
                },
                onIgnore = { version ->
                    viewModel.setIgnoredVersion(version)
                    showStartupUpdateDialog = false
                }
            )
        }
    }
}
