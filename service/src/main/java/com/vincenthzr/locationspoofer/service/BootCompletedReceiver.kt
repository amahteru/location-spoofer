package com.vincenthzr.locationspoofer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vincenthzr.locationspoofer.data.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {
    private val locationRepository: LocationRepository by inject()

    companion object {
        private const val TAG = "LocationSpoofer"

        private const val RECOVERY_TIMEOUT_MS = 8_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val appContext = context.applicationContext
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val hasRoot = withTimeoutOrNull(RECOVERY_TIMEOUT_MS) {
                            locationRepository.recoverAfterBoot(appContext)
                        }
                        android.util.Log.i(
                            TAG,
                            "BootCompletedReceiver: action=${intent.action}, recoverAfterBoot 结果 hasRoot=${hasRoot ?: "TIMEOUT"}"
                        )
                    } catch (e: Throwable) {
                        android.util.Log.w(TAG, "BootCompletedReceiver: 恢复流程异常", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
