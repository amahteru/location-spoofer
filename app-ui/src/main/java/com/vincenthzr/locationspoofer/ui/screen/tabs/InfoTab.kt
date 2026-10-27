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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.MapEngine
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

    val defaultLangText = stringResource(R.string.default_language)
    val savedLangCode = viewModel.getSavedLanguage()
    val currentLangName = remember(savedLangCode, defaultLangText) {
        LANGUAGES.firstOrNull { it.code == savedLangCode }?.nativeName ?: defaultLangText
    }

    val realismLevelNames = listOf(
        stringResource(R.string.realism_level_off),
        stringResource(R.string.realism_level_low),
        stringResource(R.string.realism_level_medium),
        stringResource(R.string.realism_level_high)
    )
    val currentRealismLevelName = realismLevelNames.getOrNull(uiState.realismLevel) ?: realismLevelNames[2]

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
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = AppSpacing.lg, bottom = AppSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(AppSpacing.lg))

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(AppSpacing.sm))

                    Text(
                        text = stringResource(R.string.app_slogan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = AppSpacing.xl)
                    )
                }
            }

            item {
                UpdateCheckCard(
                    hasNewVersion = hasNewVersion,
                    newVersionName = if (hasNewVersion) latestRelease?.versionName else null,
                    onCheckClick = onNavigateToUpdate
                )
            }

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

            item {
                Spacer(Modifier.height(AppSpacing.sm))
                FooterLinks()
            }
        }
    }
}

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
