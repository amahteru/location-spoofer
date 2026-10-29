package io.github.amahteru.locationspoofer.data.repository

import io.github.amahteru.locationspoofer.data.model.HookStatusReport
import io.github.amahteru.locationspoofer.utils.SystemFileCommands
import io.github.amahteru.locationspoofer.utils.RootManager
import io.github.amahteru.locationspoofer.vendor.HookStatusFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HookStatusRepository(private val rootManager: RootManager) {
    suspend fun readAll(): Map<String, HookStatusReport?> = withContext(Dispatchers.IO) {
        HookStatusFiles.PATHS.mapValues { (_, paths) ->
            val output = rootManager.executeCommand(SystemFileCommands.read(paths))
            HookStatusReport.parse(output)
        }
    }
}
