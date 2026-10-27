package com.vincenthzr.locationspoofer.utils

import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object XposedModuleStatus {
    private val _isModuleActive = MutableStateFlow(false)
    val isModuleActive: StateFlow<Boolean> = _isModuleActive

    private val _service = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = _service
    val mService: XposedService? get() = _service.value

    @Synchronized
    fun update(service: XposedService) {
        _service.value = service
        _isModuleActive.value = true
    }

    @Synchronized
    fun clear(service: XposedService? = null) {
        if (service != null && _service.value !== service) return
        _service.value = null
        _isModuleActive.value = false
    }
}
