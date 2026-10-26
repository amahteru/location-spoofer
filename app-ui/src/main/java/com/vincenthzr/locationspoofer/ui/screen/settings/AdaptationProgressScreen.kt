package com.vincenthzr.locationspoofer.ui.screen.settings

import com.vincenthzr.locationspoofer.ui.theme.AppMotion
import androidx.compose.animation.Crossfade
import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.progress.AdaptationProgress
import com.vincenthzr.locationspoofer.progress.AdaptationProgressSource
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.screen.RenderMarkdownContent
import com.vincenthzr.locationspoofer.vendor.VendorProfile
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

/** 系统适配页的二级页面：完整的适配进度文档 */
@Composable
fun AdaptationProgressScreen(
    document: AdaptationProgressSource.Document,
    loading: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.adaptation_progress_title),
        subtitle = stringResource(
            when {
                loading -> R.string.progress_source_loading
                document.origin == AdaptationProgressSource.Origin.REMOTE -> R.string.progress_source_remote
                document.origin == AdaptationProgressSource.Origin.CACHE -> R.string.progress_source_cache
                else -> R.string.progress_source_bundled
            }
        ),
        onBack = onClose,
        actions = {
            Crossfade(targetState = loading, animationSpec = AppMotion.defaultEffectsSpec(), label = "refresh_loading") { isLoading ->
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(AppSpacing.md).size(24.dp))
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.progress_refresh))
                    }
                }
            }
        }
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            // 页面标题已经是"适配进度"，去掉文档自己的一级标题
            RenderMarkdownContent(
                markdown = document.markdown.lineSequence()
                    .dropWhile { it.isBlank() || it.startsWith("# ") }
                    .joinToString("\n")
            )
        }
    }
}

/** 本机一句话状态：系统 + 方案 + 适配状态 */
@Composable
fun DeviceAdaptationCard(markdown: String) {
    val profile = VendorProfile.current
    val entry = remember(markdown) { AdaptationProgress.findGlobalSystem(markdown, profile.family.progressKeywords) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.systemLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(AppSpacing.md))
        StatusLine(entry?.status ?: AdaptationProgress.Status.UNVERIFIED, entry?.verifiedVersions.orEmpty())
    }
}

@Composable
private fun StatusLine(status: AdaptationProgress.Status, verifiedVersions: String) {
    val (tone, label) = when (status) {
        AdaptationProgress.Status.VERIFIED -> LabelTone.Success to R.string.progress_status_verified
        AdaptationProgress.Status.PARTIAL -> LabelTone.Warning to R.string.progress_status_partial
        AdaptationProgress.Status.BROKEN -> LabelTone.Error to R.string.progress_status_broken
        AdaptationProgress.Status.UNVERIFIED -> LabelTone.Neutral to R.string.progress_status_unverified
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusLabel(text = stringResource(label), tone = tone)
        if (verifiedVersions.isNotBlank()) {
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                stringResource(R.string.progress_verified_versions, verifiedVersions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
