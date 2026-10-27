package com.vincenthzr.locationspoofer.ui.screen.spoofing

sealed class SpoofingIntent {
    data class SetSaveDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetSavedLocationsVisible(val visible: Boolean) : SpoofingIntent()
    data class SetMapTypeDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetCustomCoordDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetStartSpoofingDialogVisible(val visible: Boolean) : SpoofingIntent()

    data class SetSheetExpanded(val expanded: Boolean) : SpoofingIntent()
    data class SetSearchActive(val active: Boolean) : SpoofingIntent()
    data class UpdateSearchQuery(val query: String) : SpoofingIntent()
    data object PerformSearch : SpoofingIntent()
    data object HideSearchResults : SpoofingIntent()
    data class ClearSearchResults(val clearAll: Boolean = false) : SpoofingIntent()
    data class SetSearchResults(
        val results: List<com.vincenthzr.locationspoofer.ui.screen.AppPoiItem>,
        val show: Boolean,
        val query: String = ""
    ) : SpoofingIntent()

    data class ConfirmMapPoint(val lat: Double, val lng: Double) : SpoofingIntent()
    data class MapPointMoved(val lat: Double, val lng: Double) : SpoofingIntent()
    data object RequestCurrentLocation : SpoofingIntent()
}
