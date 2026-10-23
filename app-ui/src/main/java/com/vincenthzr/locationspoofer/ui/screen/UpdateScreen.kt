package com.vincenthzr.locationspoofer.ui.screen

import com.vincenthzr.locationspoofer.ui.theme.AppMotion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.GithubRelease
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.UpdateUiState
import com.vincenthzr.locationspoofer.viewmodel.UpdateViewModel
import com.vincenthzr.locationspoofer.viewmodel.setCheckBetaUpdates
import com.vincenthzr.locationspoofer.viewmodel.setIgnoredVersion
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.theme.success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    updateViewModel: UpdateViewModel,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by updateViewModel.uiState.collectAsState()
    val appState by viewModel.uiState.collectAsState()
    val currentVersion = BuildConfig.VERSION_NAME
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    // 进入页面时自动检查一次最新版本
    LaunchedEffect(Unit) {
        if (uiState.releases.isEmpty()) {
            updateViewModel.fetchReleases()
        }
    }

    // 根据是否接收 Beta 通道过滤候选版本
    val filteredReleases = remember(uiState.releases, appState.checkBetaUpdates) {
        if (appState.checkBetaUpdates) {
            uiState.releases
        } else {
            uiState.releases.filter { !it.isPrerelease }
        }
    }

    // 查找比当前版本更新的未升级版本
    val missed = remember(filteredReleases, currentVersion) {
        filteredReleases.filter { isNewerVersion(it.versionName, currentVersion) }
    }

    val hasNewVersion = missed.isNotEmpty()
    val latestRelease = filteredReleases.firstOrNull()

    val displayList = remember(filteredReleases, missed) {
        if (missed.size > 1) {
            val latest = missed.first()
            val grouped = parseAndCategorizeReleaseNotes(missed)
            val mergedBody = generateMergedMarkdown(context, grouped)
            val mergedRelease = latest.copy(body = mergedBody)
            val historical = filteredReleases.filter { it !in missed }
            listOf(mergedRelease) + historical
        } else {
            filteredReleases
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.software_update)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.software_update_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 当前版本卡片（无大图标，Miuix 质感卡片，仅最新版支持忽略）
            item {
                CurrentVersionHeroCard(
                    uiState = uiState,
                    hasNewVersion = hasNewVersion,
                    currentVersion = currentVersion,
                    latestRelease = latestRelease,
                    onCheckAgain = { updateViewModel.fetchReleases() },
                    onDownload = { url, version -> updateViewModel.startDownload(url, version) },
                    onInstall = { updateViewModel.installApk() },
                    onCancelDownload = { updateViewModel.cancelDownload() },
                    onIgnore = { version ->
                        viewModel.setIgnoredVersion(version)
                        onBack()
                    }
                )
            }

            // Beta 测试版通道开关
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchListItem(
                        title = stringResource(R.string.check_beta_channel_title),
                        summary = stringResource(R.string.check_beta_channel_desc),
                        checked = appState.checkBetaUpdates,
                        onCheckedChange = { viewModel.setCheckBetaUpdates(it) }
                    )
                }
            }

            // 更新日志及历史版本列表
            if (displayList.isNotEmpty()) {
                item {
                    Text(
                        text = if (hasNewVersion) stringResource(R.string.changelog) else stringResource(R.string.version_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }

                items(displayList) { release ->
                    val cleanRelease = release.versionName.trim().lowercase().removePrefix("v")
                    val cleanCurrent = currentVersion.trim().lowercase().removePrefix("v")
                    val isCurrent = cleanRelease == cleanCurrent
                    val isMerged = missed.size > 1 && release == displayList.first()

                    ReleaseItemCard(
                        release = release,
                        isCurrentVersion = isCurrent,
                        isMergedRelease = isMerged,
                        mergedCount = missed.size,
                        uiState = uiState,
                        onDownload = { url, version -> updateViewModel.startDownload(url, version) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentVersionHeroCard(
    uiState: UpdateUiState,
    hasNewVersion: Boolean,
    currentVersion: String,
    latestRelease: GithubRelease?,
    onCheckAgain: () -> Unit,
    onDownload: (String, String) -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onIgnore: (String) -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一行：应用名，下方为方案与当前安装版本号徽章（同一行放不下，避免版本号被截断）
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Location Spoofer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                val cleanCurrentVersion = currentVersion.trim().removePrefix("v").removePrefix("V")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 当前安装的是哪个模拟方案变体，更新时只会拉取同一变体的安装包
                    com.vincenthzr.locationspoofer.ui.screen.settings.SchemeBadge()
                    StatusLabel(text = "v$cleanCurrentVersion", tone = LabelTone.Neutral)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 状态标签：检查中 / 失败 / 发现新版本 / 已是最新 之间切换时淡入淡出
            val releaseStatus = when {
                uiState.isLoading -> 0
                uiState.error != null -> 1
                hasNewVersion && latestRelease != null -> 2
                else -> 3
            }
            AnimatedContent(
                targetState = releaseStatus,
                transitionSpec = { fadeIn(AppMotion.defaultEffectsSpec()) togetherWith fadeOut(AppMotion.fastEffectsSpec()) },
                label = "release_status"
            ) { status ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (status) {
                        0 -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.checking_latest_release),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        1 -> {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.check_failed_network),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        2 -> {
                            val release = latestRelease ?: return@Row
                            val cleanLatestVersion =
                                release.versionName.trim().removePrefix("v").removePrefix("V")
                            Icon(
                                Icons.Rounded.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.new_version_found_format, cleanLatestVersion),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (release.isPrerelease) {
                                Spacer(Modifier.width(4.dp))
                                StatusLabel(text = stringResource(R.string.prerelease_badge), tone = LabelTone.Warning)
                            }
                        }
                        else -> {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.success,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.already_latest_version),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.success
                            )
                        }
                    }
                }
            }

            // 下载进度或操作按钮（非全宽，精致尺寸，最新版本独有忽略此版本选项）
            val isDownloading = uiState.activeDownloadId != null
            if (isDownloading) {
                if (uiState.downloadStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    Button(onClick = onInstall) {
                        Text(stringResource(R.string.install_new_version))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.downloading_progress, uiState.downloadProgress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onCancelDownload) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (hasNewVersion && latestRelease != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val latestDownloadUrl = latestRelease.downloadUrl
                        val latestDownloadUrl32Bit = latestRelease.downloadUrl32Bit
                        if (latestDownloadUrl != null) {
                            Button(
                                onClick = {
                                    onDownload(latestDownloadUrl, latestRelease.versionName)
                                }
                            ) {
                                Text(
                                    text = if (latestDownloadUrl32Bit != null) stringResource(R.string.update_now_64) else stringResource(R.string.update_now)
                                )
                            }
                        }

                        if (latestDownloadUrl32Bit != null) {
                            OutlinedButton(
                                onClick = {
                                    onDownload(
                                        latestDownloadUrl32Bit,
                                        "${latestRelease.versionName}_32bit"
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.download_32_compat))
                            }
                        }
                    }

                    // 仅最新版显示“忽略此版本”
                    TextButton(onClick = { onIgnore(latestRelease.versionName) }) {
                        Text(stringResource(R.string.ignore_this_version))
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onCheckAgain
                ) {
                    Text(stringResource(R.string.recheck))
                }
            }
        }
    }
}

@Composable
private fun ReleaseItemCard(
    release: GithubRelease,
    isCurrentVersion: Boolean,
    isMergedRelease: Boolean,
    mergedCount: Int,
    uiState: UpdateUiState,
    onDownload: (String, String) -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cleanReleaseVersion =
                        release.versionName.trim().removePrefix("v").removePrefix("V")
                    Text(
                        text = "v$cleanReleaseVersion",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isCurrentVersion) {
                        Spacer(Modifier.width(8.dp))
                        StatusLabel(text = stringResource(R.string.current_version), tone = LabelTone.Success)
                    }

                    if (isMergedRelease) {
                        Spacer(Modifier.width(8.dp))
                        StatusLabel(text = stringResource(R.string.merged_count_badge, mergedCount))
                    }

                    if (release.isPrerelease) {
                        Spacer(Modifier.width(8.dp))
                        StatusLabel(text = stringResource(R.string.prerelease_badge), tone = LabelTone.Warning)
                    }
                }

                if (release.publishedAt.isNotBlank()) {
                    Text(
                        text = release.publishedAt.take(10),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 更新说明详情（结构化渲染）
            RenderMarkdownContent(markdown = release.body)

            // 操作按键（历史版本仅提供下载操作，不显示忽略）
            if (!isCurrentVersion && (release.downloadUrl != null || release.downloadUrl32Bit != null)) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val releaseDownloadUrl = release.downloadUrl
                        val releaseDownloadUrl32Bit = release.downloadUrl32Bit
                        if (releaseDownloadUrl != null) {
                            Button(
                                onClick = {
                                    onDownload(releaseDownloadUrl, release.versionName)
                                }
                            ) {
                                Text(
                                    text = if (releaseDownloadUrl32Bit != null) stringResource(R.string.download_64) else stringResource(R.string.download)
                                )
                            }
                        }

                        // 仅当包含 32 位安装包时展示
                        if (releaseDownloadUrl32Bit != null) {
                            OutlinedButton(
                                onClick = {
                                    onDownload(
                                        releaseDownloadUrl32Bit,
                                        "${release.versionName}_32bit"
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.download_32))
                            }
                        }
                    }
                }
            }
        }
    }
}
