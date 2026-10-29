package io.github.amahteru.locationspoofer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.amahteru.locationspoofer.data.motion.MotionController
import io.github.amahteru.locationspoofer.data.repository.LocationRepository
import io.github.amahteru.locationspoofer.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SpoofingControlReceiver : BroadcastReceiver(), KoinComponent {
    private val locationRepository: LocationRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val motionController: MotionController by inject()

    companion object {
        const val ACTION_START = "io.github.amahteru.locationspoofer.START"
        const val ACTION_STOP = "io.github.amahteru.locationspoofer.STOP"
        const val EXTRA_FAVORITE = "favorite"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_START -> handleStart(context, intent)
                    ACTION_STOP -> handleStop(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleStart(context: Context, intent: Intent) {
        val index = intent.getIntExtra(EXTRA_FAVORITE, -1)
        val loc = settingsRepository.getSavedLocations().getOrNull(index) ?: return

        settingsRepository.isSpoofingActive = true
        settingsRepository.lastSpoofedLat = loc.lat.toString()
        settingsRepository.lastSpoofedLng = loc.lng.toString()
        val now = System.currentTimeMillis()
        locationRepository.startSpoofing(
            context, loc.lat, loc.lng,
            "STILL", 0f, now,
            emptyList(), false,
            loc.wifiJson, loc.cellJson, loc.bluetoothJson,
            settingsRepository.mockWifi, settingsRepository.mockCell, settingsRepository.mockBluetooth,
            settingsRepository.enableJitter
        )
    }

    private suspend fun handleStop(context: Context) {
        settingsRepository.isSpoofingActive = false
        locationRepository.stopSpoofing(context)
        motionController.onStopped()
    }
}
