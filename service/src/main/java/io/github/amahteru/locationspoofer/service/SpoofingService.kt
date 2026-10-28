package io.github.amahteru.locationspoofer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.amahteru.locationspoofer.data.motion.MotionController
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SpoofingService : Service(), KoinComponent {

    private val motionController: MotionController by inject()
    private val scope = MainScope()
    private var notificationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val contentIntent by lazy {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        PendingIntent.getActivity(
            this,
            0,
            launchIntent.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "SpoofingServiceChannel"
        private const val NOTIFICATION_UPDATE_MS = 2000L

        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                startSpoofing(lat, lng)
            }

            ACTION_STOP -> stopSpoofing()
        }
        return START_STICKY
    }

    @SuppressLint("WakelockTimeout")
    private fun startSpoofing(lat: Double, lng: Double) {
        acquireWakeLock()

        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(motionController.state.value.copy(lat = lat, lng = lng)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        isRunning = true

        notificationJob?.cancel()
        notificationJob = scope.launch {
            motionController.state.collect {
                if (it.active) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(it))
                delay(NOTIFICATION_UPDATE_MS)
            }
        }
    }

    private fun buildNotification(state: MotionController.State): Notification {
        val title = when {
            state.hasRoute -> R.string.spoofing_notification_route
            state.mode == MotionController.Mode.MANUAL -> R.string.spoofing_notification_joystick
            else -> R.string.spoofing_notification_point
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(title))
            .setContentText(String.format(java.util.Locale.US, "%.6f, %.6f", state.lat, state.lng))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LocationSpoofer:BackgroundSpoofingWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    private fun stopSpoofing() {
        isRunning = false
        notificationJob?.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.spoofing_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.spoofing_bg_notification_title)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isRunning) {
            acquireWakeLock()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        isRunning = false
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
