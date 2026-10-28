package io.github.amahteru.locationspoofer.ui.screen.managedata

import io.github.amahteru.locationspoofer.data.db.CompleteLocation
import io.github.amahteru.locationspoofer.data.db.CompleteCollectionRoute

data class ManageDataUiState(
    val dataList: List<CompleteLocation> = emptyList(),
    val collectionRoutes: List<CompleteCollectionRoute> = emptyList(),
    val isLoading: Boolean = false
)
