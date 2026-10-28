package io.github.amahteru.locationspoofer.data.model

import io.github.amahteru.locationspoofer.utils.AltitudeModel
import io.github.amahteru.locationspoofer.utils.MotionRealism

enum class SimMode(val speedMs: Double) {
    STILL(0.0),
    WALKING(1.4),
    RUNNING(3.0),
    CYCLING(5.5),
    DRIVING(15.0),
    CUSTOM(0.0)
}

enum class RoutePlanStage {
    IDLE,

    SELECTING,

    READY,

    RUNNING
}

enum class RouteRunMode {
    MANUAL,

    LOOP
}

enum class AppMapType {
    NORMAL,
    SATELLITE,
    MAP_3D
}

data class AppState(
    val mapType: AppMapType = AppMapType.NORMAL,
    val mapEngine: MapEngine = MapEngine.AUTO,
    val isInitializing: Boolean = true,
    val isLanguageSet: Boolean = true,
    val isPermissionSetupDone: Boolean = true,
    val currentLanguage: String = "",
    val hasRootAccess: Boolean = false,
    val rootSolution: RootSolution = RootSolution.AUTO,
    val realismLevel: Int = MotionRealism.DEFAULT_LEVEL_ID,
    val speedFluctuationPct: Int = MotionRealism.DEFAULT_SPEED_FLUCTUATION_PCT,
    val stepSimulationEnabled: Boolean = true,
    val keepLastMapPosition: Boolean = true,
    val isTestingRootSetup: Boolean = false,
    val rootSetupTestResult: RootSetupTestResult? = null,
    val isRestartingHookedApps: Boolean = false,
    val hookedAppsToRestart: List<AppInfoItem>? = null,
    val isLSPosedActive: Boolean = false,
    val longitudeInput: String = "",
    val latitudeInput: String = "",
    val showCoordinateError: Boolean = false,
    val isSavingConfig: Boolean = false,
    val isSpoofingActive: Boolean = false,
    val savedLocations: List<SavedLocation> = emptyList(),
    val simBearing: Float = 0f,
    val savedRoutes: List<SavedRoute> = emptyList(),
    val routePoints: List<RoutePoint> = emptyList(),
    val routePlanStage: RoutePlanStage = RoutePlanStage.IDLE,
    val routeRunMode: RouteRunMode = RouteRunMode.LOOP,
    val routeSimMode: SimMode = SimMode.WALKING,
    val customSpeedMs: Double = 3.0,
    val useRealRoute: Boolean = false,
    val stopAtDestination: Boolean = false,
    val isFetchingRoute: Boolean = false,
    val amapApiKey: String = "",
    val baiduApiKey: String = "",
    val googleApiKey: String = "",
    val wigleToken: String = "",
    val opencellidToken: String = "",
    val appSha1: String = "",
    val isContinuousScanning: Boolean = false,
    val isStoppingCollection: Boolean = false,
    val pendingCollectionLocations: List<io.github.amahteru.locationspoofer.data.db.LocationRecord> = emptyList(),
    val isSavingCollectionInfo: Boolean = false,
    val isRouteCollection: Boolean = false,
    val isDrawingCollectionRoute: Boolean = false,
    val collectionRoutePoints: List<RoutePoint> = emptyList(),
    val pendingCollectionRoute: io.github.amahteru.locationspoofer.data.db.CollectionRouteRecord? = null,
    val selectedCollectionRouteId: Long? = null,
    val environmentRecordCount: Int = 0,
    val scannedWifiCount: Int = 0,
    val scannedCellCount: Int = 0,
    val scannedBluetoothCount: Int = 0,
    val collectedWifiJson: String = "[]",
    val collectedCellJson: String = "[]",
    val collectedBluetoothJson: String = "[]",
    val mockWifi: Boolean = true,
    val mockCell: Boolean = true,
    val mockBluetooth: Boolean = true,
    val enableJitter: Boolean = true,
    val restartAppsOnSpoof: Boolean = true,
    val altitudeInput: String = "0.0",
    val altitudeVariationM: Int = AltitudeModel.DEFAULT_VARIATION_M,
    val satelliteCountInput: String = "20",
    val canMockWifi: Boolean = false,
    val canMockCell: Boolean = false,
    val canMockBluetooth: Boolean = false,

    val pinnedCollectedLocationId: Long? = null,
    val pinnedLocationName: String? = null,

    val checkBetaUpdates: Boolean = false
)
