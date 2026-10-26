package com.vincenthzr.locationspoofer.ui.screen.tabs

import com.vincenthzr.locationspoofer.ui.components.NavigationListItem
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import androidx.compose.material3.Card
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.MapEngine
import com.vincenthzr.locationspoofer.progress.AdaptationProgress
import com.vincenthzr.locationspoofer.progress.AdaptationProgressSource
import com.vincenthzr.locationspoofer.vendor.VendorProfile
import com.vincenthzr.locationspoofer.ui.screen.FooterLinks
import com.vincenthzr.locationspoofer.ui.screen.LANGUAGES
import com.vincenthzr.locationspoofer.ui.screen.UpdateCheckCard
import com.vincenthzr.locationspoofer.ui.screen.isNewerVersion
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.UpdateUiState
import com.vincenthzr.locationspoofer.viewmodel.getSavedLanguage
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun InfoTab(
    viewModel: MainViewModel,
    uiState: AppState,
    updateUiState: UpdateUiState? = null,
    tabBarHeight: Dp = 90.dp,
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToMapEngine: () -> Unit = {},
    onNavigateToSignatureAuth: () -> Unit = {},
    onNavigateToRootDiagnostics: () -> Unit = {},
    onNavigateToVendorScheme: () -> Unit = {},
    onNavigateToMotionRealism: () -> Unit = {},
    onNavigateToBackgroundKeepAlive: () -> Unit = {},
    onNavigateToEnvTokens: () -> Unit = {}
) {
    val currentVersion = BuildConfig.VERSION_NAME
    val targetReleases = remember(updateUiState?.releases, uiState.checkBetaUpdates) {
        val list = updateUiState?.releases ?: emptyList()
        if (uiState.checkBetaUpdates) list else list.filter { !it.isPrerelease }
    }
    val latestRelease = targetReleases.firstOrNull()
    val hasNewVersion = remember(targetReleases) {
        latestRelease != null && isNewerVersion(latestRelease.versionName, currentVersion)
    }

    // 获取当前生效的语言名称
    val defaultLangText = stringResource(R.string.default_language)
    val savedLangCode = viewModel.getSavedLanguage()
    val currentLangName = remember(savedLangCode, defaultLangText) {
        LANGUAGES.firstOrNull { it.code == savedLangCode }?.nativeName ?: defaultLangText
    }

    // 本机在适配进度表里的状态（只读本地缓存 / 内置文档，不联网），非全局方案不依赖系统适配，不显示
    // 只缓存状态本身，文案每次按当前语言取，切换应用内语言后不会残留旧语言的文字
    val progressContext = LocalContext.current
    val progressLocale = LocalConfiguration.current.locales[0]
    val adaptationStatus = remember(progressLocale) {
        val markdown = AdaptationProgressSource(progressContext).local().markdown
        val family = VendorProfile.current.family
        AdaptationProgress.findGlobalSystem(markdown, family.progressKeywords)?.status ?: AdaptationProgress.Status.UNVERIFIED
    }
    val adaptationStatusChip = adaptationStatus?.let {
        stringResource(
            when (it) {
                AdaptationProgress.Status.VERIFIED -> R.string.progress_status_verified
                AdaptationProgress.Status.PARTIAL -> R.string.progress_status_partial
                AdaptationProgress.Status.BROKEN -> R.string.progress_status_broken
                AdaptationProgress.Status.UNVERIFIED -> R.string.progress_status_unverified
            }
        )
    }

    val realismLevelNames = listOf(
        stringResource(R.string.realism_level_off),
        stringResource(R.string.realism_level_low),
        stringResource(R.string.realism_level_medium),
        stringResource(R.string.realism_level_high)
    )
    val currentRealismLevelName = realismLevelNames.getOrNull(uiState.realismLevel) ?: realismLevelNames[2]

    // 获取当前地图引擎名称
    val autoText = stringResource(R.string.map_engine_auto)
    val amapText = stringResource(R.string.map_engine_amap)
    val baiduText = stringResource(R.string.map_engine_baidu)
    val googleText = stringResource(R.string.map_engine_google)
    val currentEngineName = remember(uiState.mapEngine, autoText, amapText, baiduText, googleText) {
        when (uiState.mapEngine) {
            MapEngine.AUTO -> autoText
            MapEngine.AMAP -> amapText
            MapEngine.BAIDU -> baiduText
            MapEngine.GOOGLE -> googleText
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.lg),
            contentPadding = PaddingValues(bottom = tabBarHeight + AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // 顶部软件品牌与简介 Hero 区域（替换原先单调的信息图标与标题）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = AppSpacing.lg, bottom = AppSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 软件图标（原生平滑圆角与柔和阴影，无外层黑边缝隙）
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.large),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                android.widget.ImageView(ctx).apply {
                                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                    try {
                                        val icon =
                                            ctx.packageManager.getApplicationIcon(ctx.packageName)
                                        setImageDrawable(icon)
                                    } catch (e: Exception) {
                                        // fallback
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(AppSpacing.lg))

                    // 软件名称
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(AppSpacing.sm))

                    // 软件简介介绍
                    Text(
                        text = stringResource(R.string.app_slogan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = AppSpacing.xl)
                    )
                }
            }

            // 1. 软件更新卡片
            item {
                UpdateCheckCard(
                    hasNewVersion = hasNewVersion,
                    newVersionName = if (hasNewVersion) latestRelease?.versionName else null,
                    onCheckClick = onNavigateToUpdate
                )
            }

            // 2. 设置聚合卡片：原来的 6 个"软件配置"区块拆开后，各自独立成页，
            // 这里收进一个紧凑列表，避免变成 6 个各自占一屏高度的大卡片。
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = AppSpacing.sm)) {
                        SettingsEntryRow(
                            icon = Icons.Rounded.Language,
                            tone = LabelTone.Primary,
                            title = stringResource(R.string.select_language),
                            previewChip = currentLangName,
                            onClick = onNavigateToLanguage
                        )
                        SettingsEntryRow(
                            icon = Icons.Rounded.Map,
                            tone = LabelTone.Success,
                            title = stringResource(R.string.map_config),
                            previewChip = currentEngineName,
                            onClick = onNavigateToMapEngine
                        )
                        SettingsEntryRow(
                            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                            tone = LabelTone.Warning,
                            title = stringResource(R.string.motion_realism_title),
                            previewChip = currentRealismLevelName,
                            onClick = onNavigateToMotionRealism
                        )
                        // 本机适配状态 + 厂商适配方案（仅全局方案可选），完整进度在它的二级页面
                        SettingsEntryRow(
                            icon = Icons.Rounded.Devices,
                            tone = LabelTone.Success,
                            title = stringResource(R.string.system_adaptation_title),
                            previewChip = adaptationStatusChip,
                            onClick = onNavigateToVendorScheme
                        )
                        SettingsEntryRow(
                            icon = Icons.Rounded.Security,
                            tone = LabelTone.Warning,
                            title = stringResource(R.string.env_and_signature),
                            onClick = onNavigateToSignatureAuth
                        )
                        SettingsEntryRow(
                            icon = Icons.Rounded.Shield,
                            tone = LabelTone.Primary,
                            title = stringResource(R.string.root_solution_title),
                            onClick = onNavigateToRootDiagnostics
                        )
                        SettingsEntryRow(
                            icon = Icons.Rounded.BatteryChargingFull,
                            tone = LabelTone.Warning,
                            title = stringResource(R.string.background_keep_alive_title),
                            onClick = onNavigateToBackgroundKeepAlive
                        )
                        SettingsEntryRow(
                            icon = Icons.Rounded.VpnKey,
                            tone = LabelTone.Primary,
                            title = stringResource(R.string.env_datasource_tokens_title),
                            onClick = onNavigateToEnvTokens
                        )
                    }
                }
            }

            // 3. 底部开源协议与项目链接
            item {
                Spacer(Modifier.height(AppSpacing.sm))
                FooterLinks()
            }
        }
    }
}

/** 设置聚合卡片里的单行入口：色调图标 + 标题 + 当前值（辅助文本） + 箭头。 */
@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    tone: LabelTone,
    title: String,
    onClick: () -> Unit,
    previewChip: String? = null
) {
    NavigationListItem(
        icon = icon,
        title = title,
        summary = previewChip,
        tone = tone,
        onClick = onClick
    )
}
