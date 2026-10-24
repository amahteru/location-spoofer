package com.vincenthzr.locationspoofer.ui.screen

import com.vincenthzr.locationspoofer.ui.theme.animateListItem
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import com.vincenthzr.locationspoofer.ui.components.AppSearchField
import com.vincenthzr.locationspoofer.ui.components.EmptyState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppInfoItem
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.setAppCoordinateSystem
import com.vincenthzr.locationspoofer.ui.components.AppTopBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

private enum class CoordinateFilterTab {
    All,
    Customized,
    UserApps,
    SystemApps;

    @Composable
    fun getLabel(customCount: Int): String {
        return when (this) {
            All -> stringResource(R.string.tab_all)
            Customized -> stringResource(R.string.customized_count_format, customCount)
            UserApps -> stringResource(R.string.tab_user_apps)
            SystemApps -> stringResource(R.string.tab_system_apps)
        }
    }
}

@Composable
fun AppCoordinateScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CoordinateFilterTab.All) }
    var selectedApp by remember { mutableStateOf<AppInfoItem?>(null) }

    BackHandler(onBack = onBack)

    // 统计已自定义坐标系的应用数量
    val customConfiguredCount = remember(uiState.hookedApps, uiState.appCoordinateSystems) {
        uiState.hookedApps.count { app ->
            uiState.appCoordinateSystems.containsKey(app.packageName)
        }
    }

    val appsToShow =
        remember(uiState.hookedApps, searchQuery, selectedFilter, uiState.appCoordinateSystems) {
            uiState.hookedApps.filter { app ->
                val matchesSearch = searchQuery.isBlank() ||
                        app.appName.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)

                if (!matchesSearch) return@filter false

                val isCustom = uiState.appCoordinateSystems.containsKey(app.packageName)

                when (selectedFilter) {
                    CoordinateFilterTab.All -> true
                    CoordinateFilterTab.Customized -> isCustom
                    CoordinateFilterTab.UserApps -> !app.isSystem
                    CoordinateFilterTab.SystemApps -> app.isSystem
                }
            }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶部导航栏（独立圆形返回按键 + 标题）
            AppTopBar(
                title = stringResource(R.string.custom_coordinate_algo),
                subtitle = stringResource(R.string.app_coord_subtitle),
                onBack = onBack
            )

            // 搜索栏
            AppSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.search_app_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 分类快捷过滤标签
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CoordinateFilterTab.values()) { tab ->
                    val isSelected = selectedFilter == tab
                    val label = tab.getLabel(customConfiguredCount)

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = tab },
                        label = { Text(text = label) }
                    )
                }
            }

            // 应用列表与顶部平滑溶解边界
            if (appsToShow.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.no_hooked_apps),
                        description = stringResource(R.string.search_no_match_hint)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(appsToShow, key = { it.packageName }) { app ->
                        Box(animateListItem()) {
                            AppItemRow(
                                appInfo = app,
                                currentCoordinateSystem = uiState.appCoordinateSystems[app.packageName],
                                onClick = { selectedApp = app }
                            )
                        }
                    }
                }
            }
        }
    }

    // 现代化坐标系选择弹窗
    selectedApp?.let { app ->
        CoordinateSelectionDialog(
            appInfo = app,
            currentSystem = uiState.appCoordinateSystems[app.packageName] ?: "AUTO",
            onDismiss = { selectedApp = null },
            onSelect = { sys ->
                viewModel.setAppCoordinateSystem(app.packageName, sys)
                selectedApp = null
            }
        )
    }
}

@Composable
private fun AppItemRow(
    appInfo: AppInfoItem,
    currentCoordinateSystem: String?,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            AppIconImage(packageName = appInfo.packageName, modifier = Modifier.size(40.dp))
        },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        trailingContent = {
            // 已自定义的坐标系用主色调标签突出，未设置时显示中性的 AUTO
            StatusLabel(
                text = currentCoordinateSystem ?: "AUTO",
                tone = if (currentCoordinateSystem != null) LabelTone.Primary else LabelTone.Neutral
            )
        }
    )
}

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                imageView.setImageDrawable(icon)
            } catch (e: Exception) {
                imageView.setImageDrawable(null)
            }
        },
        modifier = modifier
    )
}
