package io.github.amahteru.locationspoofer.ui.screen.spoofing

sealed class SpoofingIntent {
    data class SetSaveDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetSavedLocationsVisible(val visible: Boolean) : SpoofingIntent()
    data class SetMapTypeDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetCustomCoordDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetStartSpoofingDialogVisible(val visible: Boolean) : SpoofingIntent()

    data class ConfirmMapPoint(val lat: Double, val lng: Double) : SpoofingIntent()
    data class MapPointMoved(val lat: Double, val lng: Double) : SpoofingIntent()
}
