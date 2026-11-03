package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.components.NavigationCard
import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.stopSpoofing
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun ActionButtons(
    viewModel: MainViewModel,
    uiState: AppState,
    onOpenMap: () -> Unit,
    onStartFixedSpoofing: () -> Unit
) {
    val spoofing = uiState.isSpoofingActive
    val containerColor by animateColorAsState(
        if (spoofing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        AppMotion.defaultEffectsSpec()
    )
    val contentColor by animateColorAsState(
        if (spoofing) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        AppMotion.defaultEffectsSpec()
    )
    Button(
        onClick = { if (spoofing) viewModel.stopSpoofing() else onStartFixedSpoofing() },
        enabled = spoofing || !uiState.isSavingConfig,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        when {
            spoofing -> Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            uiState.isSavingConfig -> CircularProgressIndicator(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                color = LocalContentColor.current,
                strokeWidth = 2.dp
            )
            else -> Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(if (spoofing) R.string.stop_simulation else R.string.fixed_simulation))
    }
}

@Composable
fun UpdateCheckCard(
    hasNewVersion: Boolean = false,
    newVersionName: String? = null,
    onCheckClick: () -> Unit
) {
    val cleanNewVersion = remember(newVersionName) {
        newVersionName?.trim()?.removePrefix("v")?.removePrefix("V")
    }
    val showNew = hasNewVersion && cleanNewVersion != null

    NavigationCard(
        icon = Icons.Rounded.SystemUpdateAlt,
        title = stringResource(R.string.check_updates),
        summary = if (showNew) stringResource(R.string.new_version_found_format, cleanNewVersion)
        else stringResource(R.string.check_updates_desc),
        onClick = onCheckClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showNew) {
                    Badge { Text("v$cleanNewVersion") }
                    Spacer(Modifier.width(AppSpacing.sm))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    )
}
