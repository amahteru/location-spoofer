package io.github.amahteru.locationspoofer.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.amahteru.locationspoofer.utils.MotionRealism
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun MainViewModel.setRealismLevel(levelId: Int) {
    settingsRepository.realismLevel = levelId
    _uiState.update { it.copy(realismLevel = levelId) }
}

internal fun MainViewModel.setSpeedFluctuationPct(pct: Int) {
    val value = pct.coerceIn(0, MotionRealism.MAX_SPEED_FLUCTUATION_PCT)
    settingsRepository.speedFluctuationPct = value
    _uiState.update { it.copy(speedFluctuationPct = value) }
}

internal fun MainViewModel.setStepSimulationEnabled(enabled: Boolean) {
    settingsRepository.stepSimulationEnabled = enabled
    _uiState.update { it.copy(stepSimulationEnabled = enabled) }
    if (_uiState.value.isSpoofingActive) {
        viewModelScope.launch { locationRepository.patchConfig { } }
    }
}

internal fun MainViewModel.setKeepLastMapPosition(enabled: Boolean) {
    settingsRepository.keepLastMapPosition = enabled
    _uiState.update { it.copy(keepLastMapPosition = enabled) }
}
