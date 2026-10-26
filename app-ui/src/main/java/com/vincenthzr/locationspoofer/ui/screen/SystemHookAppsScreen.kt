package com.vincenthzr.locationspoofer.ui.screen

import com.vincenthzr.locationspoofer.ui.theme.animateListItem
import com.vincenthzr.locationspoofer.ui.theme.onWarningContainer
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import com.vincenthzr.locationspoofer.ui.components.EmptyState
import com.vincenthzr.locationspoofer.ui.components.AppSearchField
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppInfoItem
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.loadInstalledAppsForSystemHook
import com.vincenthzr.locationspoofer.viewmodel.setSystemHookPackageEnabled
import com.vincenthzr.locationspoofer.viewmodel.setSystemHookGlobalMode
import com.vincenthzr.locationspoofer.viewmodel.setForceLocationEnabled
import com.vincenthzr.locationspoofer.viewmodel.selectAllUserAppsForSystemHook
import com.vincenthzr.locationspoofer.viewmodel.clearAllSystemHookApps
import com.vincenthzr.locationspoofer.ui.components.AppTopBar
import com.vincenthzr.locationspoofer.ui.theme.warningContainer
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

private enum class SystemHookFilterTab {
    All,
    Selected,
    UserApps,
    SystemApps;

    @Composable
    fun getLabel(selectedCount: Int): String {
        return when (this) {
            All -> stringResource(R.string.tab_all)
            Selected -> stringResource(R.string.selected_count_format, selectedCount)
            UserApps -> stringResource(R.string.tab_user_apps)
            SystemApps -> stringResource(R.string.tab_system_apps)
        }
    }
}

@Composable
fun SystemHookAppsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SystemHookFilterTab.All) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        viewModel.loadInstalledAppsForSystemHook()
    }

    val appsToShow = remember(
        uiState.installedAppsForSystemHook,
        searchQuery,
        selectedFilter,
        uiState.systemHookPackages
    ) {
        uiState.installedAppsForSystemHook.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            if (!matchesSearch) return@filter false

            val isSelected = uiState.systemHookPackages.contains(app.packageName)

            when (selectedFilter) {
                SystemHookFilterTab.All -> true
                SystemHookFilterTab.Selected -> isSelected
                SystemHookFilterTab.UserApps -> !app.isSystem
                SystemHookFilterTab.SystemApps -> app.isSystem
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.system_hook_apps_title),
            subtitle = stringResource(R.string.system_hook_apps_subtitle),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = AppSpacing.xl)
        ) {
            // 实验性功能说明
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.warningContainer,
                        contentColor = MaterialTheme.colorScheme.onWarningContainer
                    )
                ) {
                    Row(modifier = Modifier.padding(AppSpacing.lg), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(AppSpacing.md))
                        Text(text = stringResource(R.string.system_hook_apps_warning), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // 全局模拟模式
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                ) {
                    SwitchListItem(
                        title = stringResource(R.string.system_hook_global_mode_title),
                        summary = stringResource(R.string.system_hook_global_mode_desc),
                        icon = Icons.Rounded.Public,
                        checked = uiState.isSystemHookGlobalMode,
                        onCheckedChange = { viewModel.setSystemHookGlobalMode(it) }
                    )
                    if (com.vincenthzr.locationspoofer.ui.BuildConfig.GLOBAL_SCHEME &&
                        com.vincenthzr.locationspoofer.vendor.RomRules.isColorOs16(
                            com.vincenthzr.locationspoofer.vendor.VendorProfile.current)) {
                        SwitchListItem(
                            title = stringResource(R.string.coloros_virtual_location_title),
                            summary = stringResource(R.string.coloros_virtual_location_desc),
                            icon = Icons.Rounded.LocationOn,
                            checked = uiState.forceLocationEnabled,
                            onCheckedChange = { viewModel.setForceLocationEnabled(it) }
                        )
                    }
                    if (uiState.isSystemHookGlobalMode) {
                        Row(
                            modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text(
                                text = stringResource(R.string.system_hook_global_mode_active_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 搜索栏
            item {
                AppSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search_app_hint),
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)
                )
            }

            // 分类快捷过滤标签
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.sm),
                    contentPadding = PaddingValues(horizontal = AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(SystemHookFilterTab.values()) { tab ->
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { selectedFilter = tab },
                            label = { Text(text = tab.getLabel(uiState.systemHookPackages.size)) }
                        )
                    }
                }
            }

            // 批量操作与统计行
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppSpacing.lg, end = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.selected_count_format, uiState.systemHookPackages.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.selectAllUserAppsForSystemHook() }) {
                        Text(stringResource(R.string.system_hook_select_user_apps))
                    }
                    if (uiState.systemHookPackages.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearAllSystemHookApps() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.system_hook_clear_all))
                        }
                    }
                }
            }

            if (uiState.isLoadingInstalledApps && uiState.installedAppsForSystemHook.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (appsToShow.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.no_hooked_apps),
                        description = stringResource(R.string.search_no_match_hint)
                    )
                }
            } else {
                items(appsToShow, key = { it.packageName }) { app ->
                    Box(animateListItem()) {
                        SystemHookAppItemCard(
                            appInfo = app,
                            isSelected = uiState.systemHookPackages.contains(app.packageName),
                            onToggle = { enabled ->
                                viewModel.setSystemHookPackageEnabled(app.packageName, enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemHookAppItemCard(
    appInfo: AppInfoItem,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.toggleable(value = isSelected, role = Role.Switch, onValueChange = onToggle),
        leadingContent = {
            AppIconImage(packageName = appInfo.packageName, modifier = Modifier.size(40.dp))
        },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Text(
                    text = appInfo.appName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (appInfo.isSystem) {
                    StatusLabel(text = stringResource(R.string.system_tag), tone = LabelTone.Neutral)
                }
            }
        },
        supportingContent = {
            Text(text = appInfo.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = { Switch(checked = isSelected, onCheckedChange = null) }
    )
}
