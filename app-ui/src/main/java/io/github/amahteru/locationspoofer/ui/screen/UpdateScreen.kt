package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import io.github.amahteru.locationspoofer.ui.components.AppSnackbar
import io.github.amahteru.locationspoofer.ui.components.ModernSettingsInput
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.VpnKey
import io.github.amahteru.locationspoofer.ui.components.NavigationCard
import io.github.amahteru.locationspoofer.ui.components.SubPageScaffold
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.Icons
import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.BuildConfig
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.viewmodel.LatestBuild
import io.github.amahteru.locationspoofer.viewmodel.UpdateUiState
import io.github.amahteru.locationspoofer.viewmodel.UpdateViewModel
import io.github.amahteru.locationspoofer.ui.components.AppCard
import io.github.amahteru.locationspoofer.ui.theme.success
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun UpdateScreen(
    updateViewModel: UpdateViewModel,
    onBack: () -> Unit
) {
    val resources = LocalResources.current
    val uiState by updateViewModel.uiState.collectAsState()
    var showTokenDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        if (uiState.latestBuild == null) {
            updateViewModel.fetchLatestBuild()
        }
    }

    val latestBuild = uiState.latestBuild

    SubPageScaffold(
        title = stringResource(R.string.software_update),
        subtitle = stringResource(R.string.software_update_desc),
        onBack = onBack
    ) {
        CurrentVersionHeroCard(
            uiState = uiState,
            latestBuild = latestBuild,
            onCheckAgain = { updateViewModel.fetchLatestBuild() },
            onUpdate = { updateViewModel.startUpdate() },
            onInstall = { updateViewModel.installApk() },
            onCancelDownload = { updateViewModel.cancelDownload() },
            onIgnore = { sha ->
                updateViewModel.ignoreBuild(sha)
                onBack()
            }
        )

        NavigationCard(
            icon = Icons.Rounded.VpnKey,
            title = stringResource(R.string.github_token_title),
            summary = stringResource(R.string.github_token_desc),
            onClick = { showTokenDialog = true },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (uiState.hasGithubToken) R.string.github_token_set else R.string.github_token_unset
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(AppSpacing.sm))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        )
    }

    if (showTokenDialog) {
        GithubTokenDialog(
            initialToken = updateViewModel.githubToken,
            onDismiss = { showTokenDialog = false },
            onSave = { token ->
                updateViewModel.saveGithubToken(token)
                showTokenDialog = false
                AppSnackbar.show(resources.getString(R.string.save_success))
            }
        )
    }
}

@Composable
private fun GithubTokenDialog(
    initialToken: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var token by remember { mutableStateOf(initialToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) },
        title = { Text(stringResource(R.string.github_token_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                Text(stringResource(R.string.github_token_dialog_desc))
                ModernSettingsInput(
                    label = stringResource(R.string.github_token_title),
                    value = token,
                    onValueChange = { token = it },
                    placeholder = stringResource(R.string.github_token_hint),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(token) },
                enabled = token.trim() != initialToken
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun CurrentVersionHeroCard(
    uiState: UpdateUiState,
    latestBuild: LatestBuild?,
    onCheckAgain: () -> Unit,
    onUpdate: () -> Unit,
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
                Text(
                    text = stringResource(R.string.current_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val buildStatus = when {
                uiState.isLoading -> 0
                uiState.error != null -> 1
                latestBuild?.isUncomparable == true -> 4
                latestBuild?.hasUpdate == true -> 2
                else -> 3
            }
            AnimatedContent(
                targetState = buildStatus,
                transitionSpec = { fadeIn(AppMotion.defaultEffectsSpec()) togetherWith fadeOut(AppMotion.fastEffectsSpec()) },
                label = "build_status"
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
                            val build = latestBuild ?: return@Row
                            Icon(
                                Icons.Rounded.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.new_version_found_format, build.shortSha),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        4 -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.build_compare_unknown),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
            } else if (latestBuild != null && (latestBuild.hasUpdate || latestBuild.isUncomparable)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End)
                ) {
                    TextButton(onClick = { onIgnore(latestBuild.sha) }) {
                        Text(stringResource(R.string.ignore))
                    }
                    Button(
                        onClick = onUpdate,
                        enabled = !uiState.isResolvingDownload
                    ) {
                        Text(stringResource(R.string.download))
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
