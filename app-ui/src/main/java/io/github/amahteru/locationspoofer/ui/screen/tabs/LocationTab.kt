package io.github.amahteru.locationspoofer.ui.screen.tabs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.components.AppMapController
import io.github.amahteru.locationspoofer.ui.components.LocalEnvironmentDataDialog
import io.github.amahteru.locationspoofer.ui.screen.*
import io.github.amahteru.locationspoofer.ui.screen.spoofing.SpoofingIntent
import io.github.amahteru.locationspoofer.viewmodel.FavoriteToggleResult
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.ManageDataViewModel
import io.github.amahteru.locationspoofer.viewmodel.fetchCurrentLocation
import io.github.amahteru.locationspoofer.viewmodel.handleSpoofingIntent
import io.github.amahteru.locationspoofer.viewmodel.importEnvironmentData
import io.github.amahteru.locationspoofer.viewmodel.loadSavedLocation
import io.github.amahteru.locationspoofer.viewmodel.removeSavedLocation
import io.github.amahteru.locationspoofer.viewmodel.saveCurrentLocation
import io.github.amahteru.locationspoofer.viewmodel.selectCollectedRoute
import io.github.amahteru.locationspoofer.viewmodel.selectCollectedLocation
import io.github.amahteru.locationspoofer.viewmodel.setAltitude
import io.github.amahteru.locationspoofer.viewmodel.setAltitudeVariation
import io.github.amahteru.locationspoofer.viewmodel.setMapEngine
import io.github.amahteru.locationspoofer.viewmodel.setMapType
import io.github.amahteru.locationspoofer.viewmodel.setSatelliteCount
import io.github.amahteru.locationspoofer.viewmodel.startSpoofing
import io.github.amahteru.locationspoofer.viewmodel.stopSpoofing
import io.github.amahteru.locationspoofer.viewmodel.toggleCollectedLocationFavorite
import io.github.amahteru.locationspoofer.viewmodel.toggleEnableJitter
import io.github.amahteru.locationspoofer.viewmodel.toggleMockBluetooth
import io.github.amahteru.locationspoofer.viewmodel.toggleMockCell
import io.github.amahteru.locationspoofer.viewmodel.toggleMockWifi
import io.github.amahteru.locationspoofer.viewmodel.toggleRestartAppsOnSpoof
import io.github.amahteru.locationspoofer.viewmodel.updateLatitude
import io.github.amahteru.locationspoofer.viewmodel.updateLongitude
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import io.github.amahteru.locationspoofer.ui.screen.tabs.location.*
import java.util.Locale
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun LocationTab(
    viewModel: MainViewModel,
    uiState: AppState,
    mapController: AppMapController?,
    tabBarHeight: Dp = 90.dp,
    manageDataViewModel: ManageDataViewModel = koinViewModel(),
    onCollectionRouteSelected: () -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val spoofingUiState by viewModel.spoofingUiState.collectAsState()
    val onIntent = { intent: SpoofingIntent -> viewModel.handleSpoofingIntent(intent) }
    var showLocalDataDialog by remember { mutableStateOf(false) }

    var persistentSavedCardHeightPx by remember { mutableIntStateOf(0) }
    var persistentCoordCardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val savedCardHeightDp = if (persistentSavedCardHeightPx > 0) {
        with(density) { persistentSavedCardHeightPx.toDp() }
    } else {
        180.dp
    }

    val coordCardHeightDp = if (persistentCoordCardHeightPx > 0) {
        with(density) { persistentCoordCardHeightPx.toDp() }
    } else {
        210.dp
    }

    var panelState by remember {
        mutableStateOf(
            if (uiState.isSpoofingActive) LocationPanelState.COLLAPSED else LocationPanelState.DEFAULT
        )
    }

    LaunchedEffect(uiState.isSpoofingActive) {
        if (uiState.isSpoofingActive) {
            panelState = LocationPanelState.COLLAPSED
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importEnvironmentData(it) {
                Toast.makeText(context, resources.getString(R.string.import_merge_success), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var tabBottom by remember { mutableFloatStateOf(0f) }
    var panelSearchTop by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { tabBottom = it.boundsInRoot().bottom }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AppSpacing.lg, bottom = AppSpacing.lg)
                .offset { IntOffset(0, (panelSearchTop - tabBottom).roundToInt()) },
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            horizontalAlignment = Alignment.End
        ) {
            MapControlButton(
                icon = Icons.Rounded.MyLocation,
                onClick = {
                    viewModel.fetchCurrentLocation(context) { lat, lng ->
                        mapController?.animateCamera(lat, lng, 16f)
                    }
                }
            )
            MapControlButton(
                icon = Icons.Rounded.Layers,
                onClick = {
                    onIntent(SpoofingIntent.SetMapTypeDialogVisible(true))
                }
            )
            MapControlButton(
                icon = Icons.Rounded.Star,
                onClick = {
                    onIntent(SpoofingIntent.SetSavedLocationsVisible(true))
                }
            )
            MapControlButton(
                icon = Icons.Rounded.Storage,
                onClick = {
                    showLocalDataDialog = true
                }
            )
        }

        LocationControlPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            uiState = uiState,
            panelState = panelState,
            onPanelStateChange = { panelState = it },
            viewModel = viewModel,
            tabBarHeight = tabBarHeight,
            savedCardHeightDp = savedCardHeightDp,
            coordCardHeightDp = coordCardHeightDp,
            onSavedCardHeightMeasured = { persistentSavedCardHeightPx = it },
            onCoordCardHeightMeasured = { persistentCoordCardHeightPx = it },
            onSaveClick = { onIntent(SpoofingIntent.SetSaveDialogVisible(true)) },
            onCustomClick = { onIntent(SpoofingIntent.SetCustomCoordDialogVisible(true)) },
            onStartFixedSpoofing = {
                onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(true))
            },
            onStopSpoofing = { viewModel.stopSpoofing() },
            onSelectSavedLocation = { location ->
                viewModel.loadSavedLocation(location)
                mapController?.animateCamera(location.lat, location.lng, 17.5f)
                Toast.makeText(
                    context,
                    resources.getString(R.string.located_to_saved_point, location.name),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDeleteSavedLocation = { location ->
                viewModel.removeSavedLocation(location)
            },
            onOpenManageSavedLocations = {
                onIntent(SpoofingIntent.SetSavedLocationsVisible(true))
            },
            searchBar = { slotModifier ->
                PlaceSearchBar(
                    mapEngine = uiState.mapEngine,
                    onResultClick = { poi ->
                        viewModel.updateLatitude(poi.lat.toString())
                        viewModel.updateLongitude(poi.lng.toString())
                        mapController?.animateCamera(poi.lat, poi.lng, 17.5f)
                    },
                    modifier = slotModifier.onGloballyPositioned {
                        panelSearchTop = it.boundsInRoot().top
                    }
                )
            }
        )
    }

    if (showLocalDataDialog) {
        val manageDataUiState by manageDataViewModel.uiState.collectAsState()
        LocalEnvironmentDataDialog(
            dataList = manageDataUiState.dataList,
            collectionRoutes = manageDataUiState.collectionRoutes,
            onSelectRoute = { route ->
                viewModel.selectCollectedRoute(route.route.id)
                onCollectionRouteSelected()
                showLocalDataDialog = false
            },
            isLoading = manageDataUiState.isLoading,
            savedLocations = uiState.savedLocations,
            onSelectPoint = { item ->
                val lat = item.location.lat
                val lng = item.location.lng
                viewModel.selectCollectedLocation(item.location.id)
                mapController?.animateCamera(lat, lng, 17.5f)
                val label = when {
                    item.location.remark.isNotBlank() -> item.location.remark
                    item.location.placeName.isNotBlank() -> item.location.placeName
                    else -> "(${String.format(Locale.US, "%.5f", lat)}, ${
                        String.format(
                            Locale.US,
                            "%.5f",
                            lng
                        )
                    })"
                }
                Toast.makeText(
                    context,
                    resources.getString(R.string.pinned_collected_location_toast, label),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onFavorite = { item ->
                viewModel.toggleCollectedLocationFavorite(item.location.id) { result ->
                    val message = when (result) {
                        is FavoriteToggleResult.Added -> resources.getString(
                            R.string.favorited_toast,
                            result.name
                        )

                        is FavoriteToggleResult.Removed -> resources.getString(
                            R.string.unfavorited_toast,
                            result.name
                        )

                        FavoriteToggleResult.Failed -> resources.getString(R.string.favorite_failed)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onImportClick = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            },
            onDismiss = { showLocalDataDialog = false }
        )
    }

    if (spoofingUiState.showSavedLocationsDialog) {
        SavedLocationsDialog(
            savedLocations = uiState.savedLocations,
            onDismiss = { onIntent(SpoofingIntent.SetSavedLocationsVisible(false)) },
            onSelect = { location ->
                viewModel.loadSavedLocation(location)
                mapController?.animateCamera(location.lat, location.lng, 17.5f)
                onIntent(SpoofingIntent.SetSavedLocationsVisible(false))
                Toast.makeText(
                    context,
                    resources.getString(R.string.located_to_saved_point, location.name),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDelete = viewModel::removeSavedLocation
        )
    }

    if (spoofingUiState.showSaveDialog) {
        SaveNameDialog(
            title = stringResource(R.string.save_current_location),
            onConfirm = { name ->
                viewModel.saveCurrentLocation(name)
                onIntent(SpoofingIntent.SetSaveDialogVisible(false))
            },
            onDismiss = { onIntent(SpoofingIntent.SetSaveDialogVisible(false)) }
        )
    }

    if (spoofingUiState.showStartSpoofingDialog) {
        StartSpoofingDialog(
            uiState = uiState,
            onDismiss = { onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(false)) },
            onConfirm = {
                viewModel.startSpoofing()
                onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(false))
            },
            onToggleWifi = viewModel::toggleMockWifi,
            onToggleCell = viewModel::toggleMockCell,
            onToggleBluetooth = viewModel::toggleMockBluetooth,
            onToggleJitter = viewModel::toggleEnableJitter,
            onToggleRestartApps = viewModel::toggleRestartAppsOnSpoof,
            onAltitudeChange = viewModel::setAltitude,
            onAltitudeVariationChange = viewModel::setAltitudeVariation,
            onSatelliteCountChange = viewModel::setSatelliteCount
        )
    }

    if (spoofingUiState.showCustomCoordDialog) {
        CustomCoordinateDialog(
            initialLat = uiState.latitudeInput,
            initialLng = uiState.longitudeInput,
            onDismiss = { onIntent(SpoofingIntent.SetCustomCoordDialogVisible(false)) },
            onConfirm = { lat, lng ->
                viewModel.updateLatitude(lat)
                viewModel.updateLongitude(lng)
                lat.toDoubleOrNull()?.let { latVal ->
                    lng.toDoubleOrNull()?.let { lngVal ->
                        mapController?.animateCamera(latVal, lngVal, 17.5f)
                    }
                }
                onIntent(SpoofingIntent.SetCustomCoordDialogVisible(false))
            }
        )
    }

    if (spoofingUiState.showMapTypeDialog) {
        io.github.amahteru.locationspoofer.ui.components.MapTypeDialog(
            currentMapType = uiState.mapType,
            onMapTypeSelected = viewModel::setMapType,
            currentMapEngine = uiState.mapEngine,
            onMapEngineSelected = viewModel::setMapEngine,
            onDismiss = { onIntent(SpoofingIntent.SetMapTypeDialogVisible(false)) }
        )
    }
}
