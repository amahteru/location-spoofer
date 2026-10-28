package io.github.amahteru.locationspoofer

import android.app.Instrumentation
import android.os.Bundle
import io.github.amahteru.locationspoofer.utils.FrameworkConfigChannel
import io.github.amahteru.locationspoofer.utils.XposedModuleStatus
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private suspend fun moduleService(): XposedService {
    repeat(150) {
        XposedModuleStatus.mService?.let { return it }
        delay(100)
    }
    error("Framework service unavailable")
}

internal fun Instrumentation.verifyModuleRuntime(): Bundle {
    val output = Bundle()
    try {
        val service = runBlocking { moduleService() }
        output.putString("framework", "${service.frameworkName} ${service.frameworkVersion} API ${service.apiVersion}")
        output.putString("scope", service.scope.sorted().joinToString())
        if (service.apiVersion >= 102) {
            output.putString("targets", service.runningTargets.joinToString { target ->
                "${target.processName}:pid=${target.pid},state=${target.state},version=${target.loadedVersionCode}"
            })
        }
        val text = service.getRemotePreferences(FrameworkConfigChannel.GROUP)
            .getString(FrameworkConfigChannel.SNAPSHOT_KEY, null)
        if (text != null) {
            val envelope = JSONObject(text)
            val payload = envelope.optString("payload")
            output.putString("configuration", "published=${envelope.optLong("published_at")},inline=${payload.isNotEmpty()},active=${if (payload.isNotEmpty()) JSONObject(payload).optBoolean("active") else "remote-file"}")
        }
        output.putString("result", "PASS")
    } catch (error: Throwable) {
        output.putString("result", "FAIL: ${error.stackTraceToString()}")
    }
    return output
}
