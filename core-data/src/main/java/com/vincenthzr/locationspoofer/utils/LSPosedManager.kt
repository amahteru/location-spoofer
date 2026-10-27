package com.vincenthzr.locationspoofer.utils

import android.content.Context
import com.vincenthzr.locationspoofer.data.model.AppInfoItem

class LSPosedManager {
    fun isModuleActive(): Boolean {
        return XposedModuleStatus.isModuleActive.value
    }

    fun getHookedApps(context: Context): List<AppInfoItem> {
        val scope = XposedModuleStatus.mService?.scope ?: emptyList()
        val pm = context.packageManager
        return scope.mapNotNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                val isSystem =
                    (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                            (info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                AppInfoItem(pkg, label, isSystem)
            } catch (e: Exception) {
                AppInfoItem(pkg, pkg, false)
            }
        }.sortedBy { it.appName }
    }

    @android.annotation.SuppressLint("QueryPermissionsNeeded")
    fun getAllInstalledApps(context: Context): List<AppInfoItem> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        return pm.getInstalledApplications(0).mapNotNull { info ->
            if (info.packageName == selfPackage) return@mapNotNull null
            try {
                val label = pm.getApplicationLabel(info).toString()
                val isSystem =
                    (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                            (info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                AppInfoItem(info.packageName, label, isSystem)
            } catch (e: Exception) {
                AppInfoItem(info.packageName, info.packageName, false)
            }
        }.sortedBy { it.appName }
    }
}
