package io.github.amahteru.locationspoofer.ui.screen.spoofing

data class SpoofingUiState(
    val showSaveDialog: Boolean = false,
    val showSavedLocationsDialog: Boolean = false,
    val showMapTypeDialog: Boolean = false,
    val showCustomCoordDialog: Boolean = false,
    val showStartSpoofingDialog: Boolean = false
)
