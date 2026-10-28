package com.vincenthzr.locationspoofer.ui.screen.spoofing

import com.vincenthzr.locationspoofer.ui.screen.AppPoiItem

data class SpoofingUiState(
    val showSaveDialog: Boolean = false,
    val showSavedLocationsDialog: Boolean = false,
    val showMapTypeDialog: Boolean = false,
    val showCustomCoordDialog: Boolean = false,
    val showStartSpoofingDialog: Boolean = false,

    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<AppPoiItem> = emptyList(),
    val showSearchResults: Boolean = false,
    val cachedSearchQuery: String = "",
    val cachedSearchAt: Long = 0L
)
