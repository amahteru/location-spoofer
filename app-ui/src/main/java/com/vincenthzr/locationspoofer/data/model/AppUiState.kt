package com.vincenthzr.locationspoofer.data.model

import androidx.annotation.StringRes
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.utils.AltitudeModel
import com.vincenthzr.locationspoofer.vendor.VendorScheme
import com.vincenthzr.locationspoofer.utils.MotionRealism

enum class WifiLoadStatus { IDLE, LOADING, DONE }

enum class SimMode(@StringRes val labelResId: Int, val speedMs: Double) {
    STILL(R.string.still, 0.0),
    WALKING(R.string.walking, 1.4),
    RUNNING(R.string.running, 3.0),
    CYCLING(R.string.cycling, 5.5),
    DRIVING(R.string.driving, 15.0),
    CUSTOM(R.string.custom, 0.0)
}

enum class SearchMode {
    NETWORK,
    LOCAL
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
    val currentLanguage: String = "",
    val hasRootAccess: Boolean = false,
    val rootSolution: RootSolution = RootSolution.AUTO,
    val vendorScheme: VendorScheme = VendorScheme.AUTO,
    val debugDumpSystemServices: Boolean = false,
    val realismLevel: Int = MotionRealism.DEFAULT_LEVEL_ID,
    val speedFluctuationPct: Int = MotionRealism.DEFAULT_SPEED_FLUCTUATION_PCT,
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
    val wifiLoadStatus: WifiLoadStatus = WifiLoadStatus.IDLE,
    val wifiApCount: Int = 0,
    val savedLocations: List<SavedLocation> = emptyList(),
    val searchMode: SearchMode = SearchMode.NETWORK,
    val searchResults: List<SavedLocation> = emptyList(),
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
    val mapConfirmedPoint: Pair<Double, Double>? = null,
    val amapApiKey: String = "",
    val baiduApiKey: String = "",
    val googleApiKey: String = "",
    val wigleToken: String = "",
    val opencellidToken: String = "",
    val appSha1: String = "",
    val isContinuousScanning: Boolean = false,
    val isStoppingCollection: Boolean = false,
    val pendingCollectionLocations: List<com.vincenthzr.locationspoofer.data.db.LocationRecord> = emptyList(),
    val isSavingCollectionInfo: Boolean = false,
    val isRouteCollection: Boolean = false,
    val isDrawingCollectionRoute: Boolean = false,
    val collectionRoutePoints: List<RoutePoint> = emptyList(),
    val pendingCollectionRoute: com.vincenthzr.locationspoofer.data.db.CollectionRouteRecord? = null,
    val selectedCollectionRouteId: Long? = null,
    val environmentRecordCount: Int = 0,
    val scannedWifiCount: Int = 0,
    val scannedCellCount: Int = 0,
    val scannedBluetoothCount: Int = 0,
    val hookedApps: List<AppInfoItem> = emptyList(),
    val systemHookPackages: Set<String> = emptySet(),
    val isSystemHookGlobalMode: Boolean = false,
    val forceLocationEnabled: Boolean = false,
    val installedAppsForSystemHook: List<AppInfoItem> = emptyList(),
    val isLoadingInstalledApps: Boolean = false,
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
