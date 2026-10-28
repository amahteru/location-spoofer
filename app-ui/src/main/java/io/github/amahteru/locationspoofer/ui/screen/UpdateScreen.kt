package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import io.github.amahteru.locationspoofer.ui.components.SwitchListItem
import io.github.amahteru.locationspoofer.ui.components.StatusLabel
import io.github.amahteru.locationspoofer.ui.components.LabelTone
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
import io.github.amahteru.locationspoofer.ui.BuildConfig
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.GithubRelease
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.UpdateUiState
import io.github.amahteru.locationspoofer.viewmodel.UpdateViewModel
import io.github.amahteru.locationspoofer.viewmodel.setCheckBetaUpdates
import io.github.amahteru.locationspoofer.viewmodel.setIgnoredVersion
import io.github.amahteru.locationspoofer.ui.components.AppCard
import io.github.amahteru.locationspoofer.ui.theme.success
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

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

    LaunchedEffect(Unit) {
        if (uiState.releases.isEmpty()) {
            updateViewModel.fetchReleases()
        }
    }

    val filteredReleases = remember(uiState.releases, appState.checkBetaUpdates) {
        if (appState.checkBetaUpdates) {
            uiState.releases
        } else {
            uiState.releases.filter { !it.isPrerelease }
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            item {
                Text(
                    text = stringResource(R.string.software_update_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.xs)
                )
            }

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

            if (displayList.isNotEmpty()) {
                item {
                    Text(
                        text = if (hasNewVersion) stringResource(R.string.changelog) else stringResource(R.string.version_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = AppSpacing.xs, top = AppSpacing.sm)
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
        insideMargin = PaddingValues(AppSpacing.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppSpacing.sm))

                val cleanCurrentVersion = currentVersion.trim().removePrefix("v").removePrefix("V")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    StatusLabel(text = "v$cleanCurrentVersion", tone = LabelTone.Neutral)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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
                                Spacer(Modifier.width(AppSpacing.xs))
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

            val isDownloading = uiState.activeDownloadId != null
            if (isDownloading) {
                if (uiState.downloadStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    Button(onClick = onInstall) {
                        Text(stringResource(R.string.install_new_version))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
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
        insideMargin = PaddingValues(AppSpacing.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
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
                        Spacer(Modifier.width(AppSpacing.sm))
                        StatusLabel(text = stringResource(R.string.current_version), tone = LabelTone.Success)
                    }

                    if (isMergedRelease) {
                        Spacer(Modifier.width(AppSpacing.sm))
                        StatusLabel(text = stringResource(R.string.merged_count_badge, mergedCount))
                    }

                    if (release.isPrerelease) {
                        Spacer(Modifier.width(AppSpacing.sm))
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

            RenderMarkdownContent(markdown = release.body)

            if (!isCurrentVersion && (release.downloadUrl != null || release.downloadUrl32Bit != null)) {
                Spacer(Modifier.height(AppSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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
