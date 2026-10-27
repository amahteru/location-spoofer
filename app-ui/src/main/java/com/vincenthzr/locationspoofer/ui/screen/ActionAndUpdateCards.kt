package com.vincenthzr.locationspoofer.ui.screen

import com.vincenthzr.locationspoofer.ui.components.NavigationCard
import com.vincenthzr.locationspoofer.ui.components.LabelTone
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
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.stopSpoofing
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun ActionButtons(
    viewModel: MainViewModel,
    uiState: AppState,
    onOpenMap: () -> Unit,
    onStartFixedSpoofing: () -> Unit
) {
    if (uiState.isSpoofingActive) {
        Button(
            onClick = { viewModel.stopSpoofing() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.stop_simulation))
        }
    } else {
        Button(
            onClick = onStartFixedSpoofing,
            enabled = !uiState.isSavingConfig,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            if (uiState.isSavingConfig) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.starting_ellipsis))
            } else {
                Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.fixed_simulation))
            }
        }
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
        summary = if (showNew) stringResource(R.string.new_version_available_badge, cleanNewVersion!!)
        else stringResource(R.string.check_updates_desc),
        tone = if (hasNewVersion) LabelTone.Error else LabelTone.Primary,
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
