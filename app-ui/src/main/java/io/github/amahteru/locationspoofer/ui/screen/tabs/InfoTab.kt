package io.github.amahteru.locationspoofer.ui.screen.tabs

import io.github.amahteru.locationspoofer.ui.components.NavigationListItem
import io.github.amahteru.locationspoofer.ui.components.AppRootTopBar
import androidx.compose.material3.Card
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.BuildConfig
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.data.model.MapEngine
import io.github.amahteru.locationspoofer.ui.screen.LANGUAGES
import io.github.amahteru.locationspoofer.ui.screen.UpdateCheckCard
import io.github.amahteru.locationspoofer.ui.screen.isNewerVersion
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.UpdateUiState
import io.github.amahteru.locationspoofer.viewmodel.getSavedLanguage
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.github.amahteru.locationspoofer.progress.AdaptationProgress
import io.github.amahteru.locationspoofer.progress.AdaptationProgressSource
import io.github.amahteru.locationspoofer.vendor.VendorProfile

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
    onNavigateToEnvTokens: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
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

    val progressContext = LocalContext.current
    val progressLocale = LocalConfiguration.current.locales[0]
    val adaptationStatusChip = if (BuildConfig.GLOBAL_SCHEME) {
        val adaptationStatus = remember(progressLocale) {
            val markdown = AdaptationProgressSource(progressContext).local().markdown
            val family = VendorProfile.current.family
            AdaptationProgress.findGlobalSystem(markdown, family.progressKeywords)?.status ?: AdaptationProgress.Status.UNVERIFIED
        }
        stringResource(
            when (adaptationStatus) {
                AdaptationProgress.Status.VERIFIED -> R.string.progress_status_verified
                AdaptationProgress.Status.PARTIAL -> R.string.progress_status_partial
                AdaptationProgress.Status.BROKEN -> R.string.progress_status_broken
                AdaptationProgress.Status.UNVERIFIED -> R.string.progress_status_unverified
            }
        )
    } else null

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

    val entries = buildList {
        add(SettingsEntry(Icons.Rounded.Language, stringResource(R.string.select_language), onNavigateToLanguage, currentLangName))
        add(SettingsEntry(Icons.Rounded.Map, stringResource(R.string.map_config), onNavigateToMapEngine, currentEngineName))
        add(SettingsEntry(Icons.AutoMirrored.Rounded.DirectionsWalk, stringResource(R.string.motion_realism_title), onNavigateToMotionRealism, currentRealismLevelName))
        if (BuildConfig.GLOBAL_SCHEME) {
            add(SettingsEntry(Icons.Rounded.Devices, stringResource(R.string.system_adaptation_title), onNavigateToVendorScheme, adaptationStatusChip))
        }
        add(SettingsEntry(Icons.Rounded.Security, stringResource(R.string.env_and_signature), onNavigateToSignatureAuth))
        add(SettingsEntry(Icons.Rounded.Shield, stringResource(R.string.root_solution_title), onNavigateToRootDiagnostics))
        add(SettingsEntry(Icons.Rounded.BatteryChargingFull, stringResource(R.string.background_keep_alive_title), onNavigateToBackgroundKeepAlive))
        add(SettingsEntry(Icons.Rounded.VpnKey, stringResource(R.string.env_datasource_tokens_title), onNavigateToEnvTokens))
        add(SettingsEntry(Icons.Rounded.Info, stringResource(R.string.about_title), onNavigateToAbout))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppRootTopBar(
            title = stringResource(R.string.tab_info),
            subtitle = stringResource(R.string.info_tab_desc)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.lg),
            contentPadding = PaddingValues(bottom = tabBarHeight + AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
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
                        entries.forEach { entry ->
                            NavigationListItem(
                                icon = entry.icon,
                                title = entry.title,
                                summary = entry.summary,
                                onClick = entry.onClick
                            )
                        }
                    }
                }
            }
        }
    }
}

private class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val onClick: () -> Unit,
    val summary: String? = null
)
