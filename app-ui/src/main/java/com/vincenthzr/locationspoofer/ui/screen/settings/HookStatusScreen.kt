package com.vincenthzr.locationspoofer.ui.screen.settings

import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Card
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.HookStatusReport
import com.vincenthzr.locationspoofer.data.model.HookStatusReport.State
import com.vincenthzr.locationspoofer.ui.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vincenthzr.locationspoofer.ui.components.AppCard

/** 本次开机的时间点，早于它写出的报告说明本次开机模块没有在该进程里生效 */
internal fun bootTimeMs(): Long = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()

/** 一级页面上的一句话概括，例如"8/9 个组件已挂载"；没有任何有效报告时返回 null */
internal fun hookStatusSummary(reports: Map<String, HookStatusReport?>): Pair<Int, Int>? {
    val fresh = reports.values.filterNotNull().filterNot { it.isStale(bootTimeMs()) }
    if (fresh.isEmpty()) return null
    return fresh.sumOf { it.hookedCount } to fresh.sumOf { it.components.size }
}

/** 系统适配页的二级页面：各系统进程的 Hook 运行状态 */
@Composable
fun HookStatusScreen(
    reports: Map<String, HookStatusReport?>,
    loading: Boolean,
    onRefresh: () -> Unit,
    debugDump: Boolean,
    onDebugDumpChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val boot = remember(reports) { bootTimeMs() }
    val vendorReport = reports.values.firstOrNull { it != null && !it.isStale(boot) }

    SubPageScaffold(
        title = stringResource(R.string.hook_status_title),
        subtitle = vendorReport?.let {
            stringResource(
                if (it.manualVendor) R.string.hook_status_adapter_manual else R.string.hook_status_adapter_auto,
                it.vendorId
            )
        },
        onBack = onClose,
        actions = {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp))
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.progress_refresh))
                }
            }
        }
    ) {
        reports.forEach { (process, report) -> ProcessCard(process, report, boot) }

        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchListItem(
                title = stringResource(R.string.hook_status_debug_dump),
                summary = stringResource(R.string.hook_status_debug_dump_desc),
                checked = debugDump,
                onCheckedChange = onDebugDumpChange
            )
        }
    }
}

@Composable
private fun ProcessCard(process: String, report: HookStatusReport?, bootTime: Long) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp)
    ) {
        Text(
            stringResource(processName(process)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        when {
            report == null -> Hint(stringResource(R.string.hook_status_no_report))
            report.isStale(bootTime) -> Hint(stringResource(R.string.hook_status_stale))
            else -> {
                Hint(
                    stringResource(
                        R.string.hook_status_updated_at,
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(report.updatedAt))
                    )
                )
                Hint(
                    if (report.configPath.isBlank()) stringResource(R.string.hook_status_config_none)
                    else stringResource(
                        R.string.hook_status_config,
                        report.configPath,
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(report.configModified))
                    )
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.components.forEach { ComponentRow(it) }
                }
                if (report.errors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.hook_status_errors),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    report.errors.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(component: HookStatusReport.Component) {
    val (tone, label) = when (component.state) {
        State.HOOKED -> LabelTone.Success to R.string.hook_state_hooked
        State.NO_METHODS -> LabelTone.Warning to R.string.hook_state_no_methods
        State.NOT_FOUND -> LabelTone.Error to R.string.hook_state_not_found
        State.PENDING -> LabelTone.Neutral to R.string.hook_state_pending
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    componentName(component.name)?.let { stringResource(it) } ?: component.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StatusLabel(text = stringResource(label), tone = tone)
            }
            if (component.found) {
                Text(
                    component.className,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.hook_status_source, component.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val (hooked, absent) = component.methods.entries.partition { it.value > 0 }
                if (hooked.isNotEmpty()) {
                    Text(
                        hooked.joinToString("  ") { (name, count) -> "$name ×$count" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (absent.isNotEmpty()) {
                    // 全部 ×0 时说明类找对了但方法都不在，要重点看；部分 ×0 多为兼容其他 Android 版本的备用方法名
                    Text(
                        stringResource(R.string.hook_status_absent_methods, absent.joinToString(", ") { it.key }),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hooked.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@StringRes
private fun processName(process: String): Int = when (process) {
    "SYSTEM_SERVER" -> R.string.hook_process_system_server
    "PHONE" -> R.string.hook_process_phone
    "BLUETOOTH" -> R.string.hook_process_bluetooth
    else -> R.string.hook_process_unknown
}

/** xposed 模块 SystemComponent 的显示名；新增组件时在这里和字符串资源里补上，没补时直接显示枚举名 */
@StringRes
private fun componentName(name: String): Int? = when (name) {
    "LOCATION_MANAGER_SERVICE" -> R.string.hook_component_location_manager_service
    "LOCATION_PROVIDER_MANAGER" -> R.string.hook_component_location_provider_manager
    "WIFI_SERVICE" -> R.string.hook_component_wifi_service
    "WIFI_SCANNER_SERVICE" -> R.string.hook_component_wifi_scanner_service
    "CONNECTIVITY_SERVICE" -> R.string.hook_component_connectivity_service
    "TELEPHONY_REGISTRY" -> R.string.hook_component_telephony_registry
    "APPOPS_SERVICE" -> R.string.hook_component_appops_service
    "TELEPHONY_PHONE_MANAGER" -> R.string.hook_component_telephony_phone_manager
    "BLUETOOTH_SCAN_SERVICE" -> R.string.hook_component_bluetooth_scan_service
    else -> null
}
