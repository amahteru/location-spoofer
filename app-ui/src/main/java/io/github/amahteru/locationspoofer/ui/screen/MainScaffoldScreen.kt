package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.theme.AppMotion
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
import io.github.amahteru.locationspoofer.ui.BuildConfig
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.SubPageOverlay
import io.github.amahteru.locationspoofer.ui.components.AppMapView
import io.github.amahteru.locationspoofer.ui.screen.spoofing.SpoofingIntent
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.UpdateViewModel
import io.github.amahteru.locationspoofer.viewmodel.getIgnoredVersion
import io.github.amahteru.locationspoofer.viewmodel.handleSpoofingIntent
import io.github.amahteru.locationspoofer.viewmodel.isDomesticEnvironment
import io.github.amahteru.locationspoofer.viewmodel.setIgnoredVersion
import org.koin.androidx.compose.koinViewModel
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

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
    ScannerMap,
    ManageData,
    Update,
    LanguageSettings,
    MapEngineSettings,
    SignatureAuth,
    RootDiagnostics,
    MotionRealism,
    BackgroundKeepAlive,
    EnvTokens
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

    LaunchedEffect(Unit) {
        updateViewModel.fetchReleases()
    }

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

    var mapEngineOnScreen by remember { mutableStateOf(uiState.mapEngine) }
    LaunchedEffect(currentSubScreen, uiState.mapEngine) {
        if (currentSubScreen == MainSubScreen.None) mapEngineOnScreen = uiState.mapEngine
    }

    BackHandler(enabled = currentSubScreen == MainSubScreen.None && selectedTab != BottomTab.Location.ordinal) {
        selectedTab = BottomTab.Location.ordinal
    }

    LaunchedEffect(selectedTab, mapController, uiState.mapType) {
        if (selectedTab == 0) {
            mapController?.clear()
            mapController?.setMapType(uiState.mapType)
        }
    }

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
                Box(modifier = Modifier.fillMaxSize()) {
                    AppMapView(
                        modifier = Modifier.fillMaxSize(),
                        mapEngine = mapEngineOnScreen,
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
                                .padding(bottom = AppSpacing.lg)
                        )
                    }
                }

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        motion.pageSlideTransition(if (targetState > initialState) 1 else -1)
                    },
                    label = "main_tabs_transition"
                ) { targetTab ->
                    when (targetTab) {
                        BottomTab.Location.ordinal -> io.github.amahteru.locationspoofer.ui.screen.tabs.LocationTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            mapController = mapController,
                            onCollectionRouteSelected = { selectedTab = BottomTab.Route.ordinal },
                            tabBarHeight = paddingValues.calculateBottomPadding()
                        )

                        BottomTab.Route.ordinal -> io.github.amahteru.locationspoofer.ui.screen.tabs.RouteTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            mapController = mapController,
                            isActive = true,
                            bottomBarHeight = paddingValues.calculateBottomPadding()
                        )

                        BottomTab.Features.ordinal -> io.github.amahteru.locationspoofer.ui.screen.tabs.FeaturesTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            tabBarHeight = paddingValues.calculateBottomPadding(),
                            onNavigateToScanner = { currentSubScreen = MainSubScreen.ScannerMap },
                            onNavigateToManageData = { currentSubScreen = MainSubScreen.ManageData }
                        )

                        BottomTab.Info.ordinal -> io.github.amahteru.locationspoofer.ui.screen.tabs.InfoTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            updateUiState = updateUiState,
                            tabBarHeight = paddingValues.calculateBottomPadding(),
                            onNavigateToUpdate = { currentSubScreen = MainSubScreen.Update },
                            onNavigateToLanguage = { currentSubScreen = MainSubScreen.LanguageSettings },
                            onNavigateToMapEngine = { currentSubScreen = MainSubScreen.MapEngineSettings },
                            onNavigateToSignatureAuth = { currentSubScreen = MainSubScreen.SignatureAuth },
                            onNavigateToRootDiagnostics = { currentSubScreen = MainSubScreen.RootDiagnostics },
                            onNavigateToMotionRealism = { currentSubScreen = MainSubScreen.MotionRealism },
                            onNavigateToBackgroundKeepAlive = { currentSubScreen = MainSubScreen.BackgroundKeepAlive },
                            onNavigateToEnvTokens = { currentSubScreen = MainSubScreen.EnvTokens }
                        )
                    }
                }
            }
        }

        SubPageOverlay(visible = currentSubScreen != MainSubScreen.None) {
            val subScreenToRender =
                if (currentSubScreen != MainSubScreen.None) currentSubScreen else activeSubScreen
            Box(modifier = Modifier.fillMaxSize()) {
                when (subScreenToRender) {
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

                    MainSubScreen.LanguageSettings -> io.github.amahteru.locationspoofer.ui.screen.settings.LanguageSettingsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.MapEngineSettings -> io.github.amahteru.locationspoofer.ui.screen.settings.MapEngineSettingsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.SignatureAuth -> io.github.amahteru.locationspoofer.ui.screen.settings.SignatureAuthScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.RootDiagnostics -> io.github.amahteru.locationspoofer.ui.screen.settings.RootDiagnosticsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.MotionRealism -> io.github.amahteru.locationspoofer.ui.screen.settings.MotionRealismScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.BackgroundKeepAlive -> io.github.amahteru.locationspoofer.ui.screen.settings.BackgroundKeepAliveScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.EnvTokens -> io.github.amahteru.locationspoofer.ui.screen.settings.EnvTokensScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = { currentSubScreen = MainSubScreen.None }
                    )

                    MainSubScreen.None -> Unit
                }
            }
        }

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
