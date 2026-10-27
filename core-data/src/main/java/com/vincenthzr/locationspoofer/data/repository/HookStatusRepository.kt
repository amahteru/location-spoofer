package com.vincenthzr.locationspoofer.data.repository

import com.vincenthzr.locationspoofer.data.model.HookStatusReport
import com.vincenthzr.locationspoofer.utils.SystemFileCommands
import com.vincenthzr.locationspoofer.utils.RootManager
import com.vincenthzr.locationspoofer.vendor.HookStatusFiles
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
