package io.github.amahteru.locationspoofer.data.repository

import io.github.amahteru.locationspoofer.data.model.SavedLocation
import io.github.amahteru.locationspoofer.data.model.SavedRoute
import io.github.amahteru.locationspoofer.utils.SettingsManager

class SettingsRepository(private val settingsManager: SettingsManager) {

    fun getSavedLocations(): List<SavedLocation> = settingsManager.getSavedLocations()

    fun setSavedLocations(locations: List<SavedLocation>) = settingsManager.saveLocationList(locations)

    fun addSavedLocation(location: SavedLocation) = settingsManager.addSavedLocation(location)

    fun removeSavedLocation(location: SavedLocation) = settingsManager.removeSavedLocation(location)

    fun getSavedRoutes(): List<SavedRoute> = settingsManager.getSavedRoutes()

    fun addSavedRoute(route: SavedRoute) = settingsManager.addSavedRoute(route)

    fun removeSavedRoute(route: SavedRoute) = settingsManager.removeSavedRoute(route)

    fun isLanguageSet(): Boolean = settingsManager.isLanguageSet

    fun setLanguageSet(value: Boolean) {
        settingsManager.isLanguageSet = value
    }

    fun isPermissionSetupDone(): Boolean = settingsManager.isPermissionSetupDone

    fun setPermissionSetupDone(value: Boolean) {
        settingsManager.isPermissionSetupDone = value
    }

    fun getBlockedPermissions(): Set<String> = settingsManager.blockedPermissions

    fun setBlockedPermissions(value: Set<String>) {
        settingsManager.blockedPermissions = value
    }

    fun getLanguage(): String = settingsManager.language

    fun setLanguage(value: String) {
        settingsManager.language = value
    }

    fun getAmapApiKey(): String = settingsManager.amapApiKey

    fun setAmapApiKey(value: String) {
        settingsManager.amapApiKey = value
    }

    fun getBaiduApiKey(): String = settingsManager.baiduApiKey

    fun setBaiduApiKey(value: String) {
        settingsManager.baiduApiKey = value
    }

    fun getGoogleApiKey(): String = settingsManager.googleApiKey

    fun setGoogleApiKey(value: String) {
        settingsManager.googleApiKey = value
    }

    fun getWigleApiToken(): String = settingsManager.wigleApiToken

    fun setWigleApiToken(value: String) {
        settingsManager.wigleApiToken = value
    }

    fun getOpencellidApiToken(): String = settingsManager.opencellidApiToken

    fun setOpencellidApiToken(value: String) {
        settingsManager.opencellidApiToken = value
    }

    fun getMapType(): String = settingsManager.mapType

    fun setMapType(value: String) {
        settingsManager.mapType = value
    }

    fun getMapEngine(): String = settingsManager.mapEngine

    fun setMapEngine(value: String) {
        settingsManager.mapEngine = value
    }

    fun getRootSolution(): String = settingsManager.rootSolution

    fun setRootSolution(value: String) {
        settingsManager.rootSolution = value
    }

    fun getVendorOverride(): String = settingsManager.vendorOverride

    fun setVendorOverride(value: String) {
        settingsManager.vendorOverride = value
    }

    var debugDumpSystemServices: Boolean
        get() = settingsManager.debugDumpSystemServices
        set(value) {
            settingsManager.debugDumpSystemServices = value
        }

    fun getIgnoredVersion(): String = settingsManager.ignoredVersion

    fun setIgnoredVersion(value: String) {
        settingsManager.ignoredVersion = value
    }

    fun getSystemHookPackages(): Set<String> = settingsManager.getSystemHookPackages()

    fun setSystemHookPackages(packages: Set<String>) =
        settingsManager.setSystemHookPackages(packages)

    var isSystemHookGlobalMode: Boolean
        get() = settingsManager.isSystemHookGlobalMode
        set(value) {
            settingsManager.isSystemHookGlobalMode = value
        }

    var forceLocationEnabled: Boolean
        get() = settingsManager.forceLocationEnabled
        set(value) { settingsManager.forceLocationEnabled = value }

    var isSpoofingActive: Boolean
        get() = settingsManager.isSpoofingActive
        set(value) {
            settingsManager.isSpoofingActive = value
        }

    var lastSpoofedLat: String
        get() = settingsManager.lastSpoofedLat
        set(value) {
            settingsManager.lastSpoofedLat = value
        }

    var lastSpoofedLng: String
        get() = settingsManager.lastSpoofedLng
        set(value) {
            settingsManager.lastSpoofedLng = value
        }

    var mockWifi: Boolean
        get() = settingsManager.mockWifi
        set(value) {
            settingsManager.mockWifi = value
        }

    var mockCell: Boolean
        get() = settingsManager.mockCell
        set(value) {
            settingsManager.mockCell = value
        }

    var mockBluetooth: Boolean
        get() = settingsManager.mockBluetooth
        set(value) {
            settingsManager.mockBluetooth = value
        }

    var enableJitter: Boolean
        get() = settingsManager.enableJitter
        set(value) {
            settingsManager.enableJitter = value
        }

    var restartAppsOnSpoof: Boolean
        get() = settingsManager.restartAppsOnSpoof
        set(value) {
            settingsManager.restartAppsOnSpoof = value
        }

    var altitude: String
        get() = settingsManager.altitude
        set(value) {
            settingsManager.altitude = value
        }

    var altitudeVariationM: Int
        get() = settingsManager.altitudeVariationM
        set(value) {
            settingsManager.altitudeVariationM = value
        }

    var realismLevel: Int
        get() = settingsManager.realismLevel
        set(value) {
            settingsManager.realismLevel = value
        }

    var speedFluctuationPct: Int
        get() = settingsManager.speedFluctuationPct
        set(value) {
            settingsManager.speedFluctuationPct = value
        }

    var stepSimulationEnabled: Boolean
        get() = settingsManager.stepSimulationEnabled
        set(value) {
            settingsManager.stepSimulationEnabled = value
        }

    var keepLastMapPosition: Boolean
        get() = settingsManager.keepLastMapPosition
        set(value) {
            settingsManager.keepLastMapPosition = value
        }

    fun getLastMapPosition(): Pair<String, String>? {
        val lat = settingsManager.lastMapLat
        val lng = settingsManager.lastMapLng
        return if (lat.isNotBlank() && lng.isNotBlank()) lat to lng else null
    }

    fun setLastMapPosition(lat: String, lng: String) {
        settingsManager.lastMapLat = lat
        settingsManager.lastMapLng = lng
    }

    var satelliteCount: String
        get() = settingsManager.satelliteCount
        set(value) {
            settingsManager.satelliteCount = value
        }

    var checkBetaUpdates: Boolean
        get() = settingsManager.checkBetaUpdates
        set(value) {
            settingsManager.checkBetaUpdates = value
        }
}
