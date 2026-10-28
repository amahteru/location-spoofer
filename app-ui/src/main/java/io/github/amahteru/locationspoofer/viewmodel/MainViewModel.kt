package io.github.amahteru.locationspoofer.viewmodel

import android.content.Context
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.data.db.EnvironmentDao
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.data.model.AppMapType
import io.github.amahteru.locationspoofer.data.model.MapEngine
import io.github.amahteru.locationspoofer.data.model.RootSolution
import io.github.amahteru.locationspoofer.data.motion.MotionController
import io.github.amahteru.locationspoofer.data.repository.LocationRepository
import io.github.amahteru.locationspoofer.data.repository.SettingsRepository
import io.github.amahteru.locationspoofer.data.repository.WifiRepository
import io.github.amahteru.locationspoofer.ui.screen.spoofing.SpoofingUiState
import io.github.amahteru.locationspoofer.utils.EnvironmentScanner
import io.github.amahteru.locationspoofer.utils.LSPosedManager
import io.github.amahteru.locationspoofer.utils.OpenCellIdClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

sealed interface FavoriteToggleResult {
    data class Added(val name: String) : FavoriteToggleResult
    data class Removed(val name: String) : FavoriteToggleResult
    data object Failed : FavoriteToggleResult
}

class MainViewModel(
    internal val locationRepository: LocationRepository,
    internal val settingsRepository: SettingsRepository,
    internal val lsposedManager: LSPosedManager,
    internal val environmentScanner: EnvironmentScanner,
    internal val environmentDao: EnvironmentDao,
    internal val wifiRepository: WifiRepository,
    internal val opencellidClient: OpenCellIdClient,
    internal val motionController: MotionController,
    application: Application
) : AndroidViewModel(application) {
    internal val context: Context get() = getApplication()
    internal var lastMapMoveTime = 0L
    internal var mapMoveJob: Job? = null
    internal var mockCapabilitiesJob: Job? = null
    internal var environmentUpdateJob: Job? = null

    val collectionRoutes = environmentDao.observeCollectionRoutes().distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val environmentLocations: StateFlow<List<CompleteLocation>> = environmentDao
        .observeAllCompleteLocations()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    internal val _uiState = MutableStateFlow(
        AppState(
            mapType = try {
                AppMapType.valueOf(settingsRepository.getMapType())
            } catch (e: Exception) {
                AppMapType.NORMAL
            },
            mapEngine = try {
                MapEngine.valueOf(settingsRepository.getMapEngine())
            } catch (e: Exception) {
                MapEngine.AUTO
            },
            rootSolution = try {
                RootSolution.valueOf(settingsRepository.getRootSolution())
            } catch (e: Exception) {
                RootSolution.AUTO
            },
            realismLevel = settingsRepository.realismLevel,
            speedFluctuationPct = settingsRepository.speedFluctuationPct,
            stepSimulationEnabled = settingsRepository.stepSimulationEnabled,
            keepLastMapPosition = settingsRepository.keepLastMapPosition,
            savedLocations = settingsRepository.getSavedLocations(),
            savedRoutes = emptyList(),
            currentLanguage = settingsRepository.getLanguage(),
            isLanguageSet = settingsRepository.isLanguageSet(),
            isPermissionSetupDone = settingsRepository.isPermissionSetupDone(),
            mockWifi = settingsRepository.mockWifi,
            mockCell = settingsRepository.mockCell,
            mockBluetooth = settingsRepository.mockBluetooth,
            enableJitter = settingsRepository.enableJitter,
            restartAppsOnSpoof = settingsRepository.restartAppsOnSpoof,
            altitudeInput = settingsRepository.altitude,
            altitudeVariationM = settingsRepository.altitudeVariationM,
            satelliteCountInput = settingsRepository.satelliteCount,
            wigleToken = settingsRepository.getWigleApiToken(),
            opencellidToken = settingsRepository.getOpencellidApiToken()
        )
    )
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    internal val _spoofingUiState =
        MutableStateFlow(SpoofingUiState())
    val spoofingUiState: StateFlow<SpoofingUiState> =
        _spoofingUiState.asStateFlow()

    internal var locationSyncJob: Job? = null
    internal var continuousScanJob: Job? = null

    init {
        initialize()
    }

    internal var pinnedLocationRecordId: Long? = null

    internal val favoriteToggleMutex = kotlinx.coroutines.sync.Mutex()

    internal var lastEnvironmentRouteId: Long? = null
    internal var lastDbQueryLat: Double = 0.0
    internal var lastDbQueryLng: Double = 0.0

    internal var restoredSpoofPoint: io.github.amahteru.locationspoofer.utils.CoordinateUtils.LatLng? = null
}
