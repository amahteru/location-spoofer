package io.github.amahteru.locationspoofer.data.repository

import android.content.Context

interface SpoofingServiceController {
    val isRunning: Boolean
    fun startForeground(context: Context, lat: Double, lng: Double)
    fun stop(context: Context)
    fun startCollection(context: Context)
    fun stopCollection(context: Context)

    val isFloatingJoystickShowing: Boolean

    fun setFloatingJoystickVisible(context: Context, visible: Boolean)
}
