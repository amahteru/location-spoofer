package com.vincenthzr.locationspoofer.ui.screen.settings

import com.vincenthzr.locationspoofer.ui.components.ToneIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.Card
import androidx.compose.foundation.clickable
import com.vincenthzr.locationspoofer.ui.components.SectionTitle
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import com.vincenthzr.locationspoofer.ui.components.SubPageOverlay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.HookStatusReport
import com.vincenthzr.locationspoofer.data.repository.HookStatusRepository
import com.vincenthzr.locationspoofer.progress.AdaptationProgressSource
import com.vincenthzr.locationspoofer.viewmodel.setDebugDumpSystemServices
import org.koin.compose.koinInject
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.vendor.VendorScheme
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.setVendorScheme
import kotlinx.coroutines.launch
import com.vincenthzr.locationspoofer.ui.components.AppCard
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendorSchemeScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // 文档按语言区分中英文版，语言变了要换一份
    val source = remember(LocalConfiguration.current.locales[0]) { AdaptationProgressSource(context) }
    var document by remember(source) { mutableStateOf(source.local()) }
    var loading by remember { mutableStateOf(false) }
    var showFullProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 全局方案：各系统进程写出的 Hook 运行状态报告
    val hookStatusRepository = koinInject<HookStatusRepository>()
    var hookReports by remember { mutableStateOf<Map<String, HookStatusReport?>>(emptyMap()) }
    var hookLoading by remember { mutableStateOf(false) }
    var showHookStatus by remember { mutableStateOf(false) }

    fun refreshHookStatus() {
        if (!BuildConfig.GLOBAL_SCHEME || hookLoading) return
        hookLoading = true
        scope.launch {
            hookReports = hookStatusRepository.readAll()
            hookLoading = false
        }
    }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            source.fetchRemote()?.let { document = it }
            loading = false
        }
    }
    LaunchedEffect(Unit) {
        refreshHookStatus()
    }
    // 换语言后重新拉取对应语言的文档
    LaunchedEffect(source) {
        refresh()
    }

    BackHandler(enabled = !showHookStatus && !showFullProgress, onBack = onClose)

    // 下级页面（Hook 状态 / 完整适配进度）以覆盖层滑入，上级页面保留在下方
    Box(modifier = Modifier.fillMaxSize()) {
        VendorSchemeContent(
            viewModel = viewModel,
            uiState = uiState,
            markdown = document.markdown,
            hookReports = hookReports,
            hookLoading = hookLoading,
            onClose = onClose,
            onOpenHookStatus = { showHookStatus = true },
            onOpenFullProgress = { showFullProgress = true }
        )
        SubPageOverlay(visible = showHookStatus) {
            HookStatusScreen(
                reports = hookReports,
                loading = hookLoading,
                onRefresh = { refreshHookStatus() },
                debugDump = uiState.debugDumpSystemServices,
                onDebugDumpChange = { viewModel.setDebugDumpSystemServices(it) },
                onClose = { showHookStatus = false }
            )
        }
        SubPageOverlay(visible = showFullProgress) {
            AdaptationProgressScreen(
                document = document,
                loading = loading,
                onRefresh = { refresh() },
                onClose = { showFullProgress = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VendorSchemeContent(
    viewModel: MainViewModel,
    uiState: AppState,
    markdown: String,
    hookReports: Map<String, HookStatusReport?>,
    hookLoading: Boolean,
    onClose: () -> Unit,
    onOpenHookStatus: () -> Unit,
    onOpenFullProgress: () -> Unit
) {
    SubPageScaffold(
        title = stringResource(R.string.system_adaptation_title),
        subtitle = null,
        onBack = onClose
    ) {
        DeviceAdaptationCard(markdown)

        // 厂商适配只作用于系统级 Hook，仅全局方案可选
        if (BuildConfig.GLOBAL_SCHEME) {
            SectionTitle(stringResource(R.string.vendor_scheme_title))
            Text(
                text = stringResource(R.string.vendor_scheme_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.xs)
            )
        }
        if (BuildConfig.GLOBAL_SCHEME) AppCard(modifier = Modifier.fillMaxWidth()) {
            val schemes = listOf(
                VendorScheme.AUTO to stringResource(R.string.vendor_scheme_auto),
                VendorScheme.HYPEROS to stringResource(R.string.vendor_scheme_hyperos),
                VendorScheme.COLOROS to stringResource(R.string.vendor_scheme_coloros),
                VendorScheme.ONEUI to stringResource(R.string.vendor_scheme_oneui),
                VendorScheme.AOSP to stringResource(R.string.vendor_scheme_aosp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                schemes.forEach { (scheme, label) ->
                    FilterChip(
                        selected = uiState.vendorScheme == scheme,
                        onClick = { viewModel.setVendorScheme(scheme) },
                        label = { Text(text = label) }
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Info,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = stringResource(R.string.vendor_scheme_reboot_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (BuildConfig.GLOBAL_SCHEME) {
            val summary = hookStatusSummary(hookReports)
            NavigationRowCard(
                icon = Icons.Rounded.Analytics,
                title = stringResource(R.string.hook_status_title),
                chip = when {
                    hookLoading && hookReports.isEmpty() -> null
                    summary == null -> stringResource(R.string.hook_status_summary_none)
                    else -> stringResource(R.string.hook_status_summary, summary.first, summary.second)
                },
                chipTone = when {
                    summary == null -> LabelTone.Neutral
                    summary.first == summary.second -> LabelTone.Success
                    else -> LabelTone.Warning
                },
                onClick = onOpenHookStatus
            )
        }

        NavigationRowCard(
            icon = Icons.Rounded.TaskAlt,
            title = stringResource(R.string.adaptation_progress_full),
            onClick = onOpenFullProgress
        )
    }
}

/** 一行可点击的卡片：图标 + 标题 + 可选的状态标签（标题下方）+ 箭头，点进二级页面 */
@Composable
private fun NavigationRowCard(
    icon: ImageVector,
    title: String,
    chip: String? = null,
    chipTone: LabelTone = LabelTone.Neutral,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            leadingContent = { ToneIcon(icon) },
            headlineContent = { Text(title) },
            supportingContent = chip?.let { { StatusLabel(text = it, tone = chipTone, modifier = Modifier.padding(top = AppSpacing.xs)) } },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
