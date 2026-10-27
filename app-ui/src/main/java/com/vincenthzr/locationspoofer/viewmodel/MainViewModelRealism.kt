package com.vincenthzr.locationspoofer.viewmodel

import com.vincenthzr.locationspoofer.utils.MotionRealism
import kotlinx.coroutines.flow.update

internal fun MainViewModel.setRealismLevel(levelId: Int) {
    settingsRepository.realismLevel = levelId
    _uiState.update { it.copy(realismLevel = levelId) }
}

internal fun MainViewModel.setSpeedFluctuationPct(pct: Int) {
    val value = pct.coerceIn(0, MotionRealism.MAX_SPEED_FLUCTUATION_PCT)
    settingsRepository.speedFluctuationPct = value
    _uiState.update { it.copy(speedFluctuationPct = value) }
}

internal fun MainViewModel.setKeepLastMapPosition(enabled: Boolean) {
    settingsRepository.keepLastMapPosition = enabled
    _uiState.update { it.copy(keepLastMapPosition = enabled) }
}
