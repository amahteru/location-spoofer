package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.GithubRelease
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.StatusLabel
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun StartupUpdateDialog(
    latestRelease: GithubRelease,
    onDismiss: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onIgnore: (String) -> Unit
) {
    val cleanVersion = remember(latestRelease.versionName) {
        latestRelease.versionName.trim().removePrefix("v").removePrefix("V")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.new_version_found)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    StatusLabel(text = "v$cleanVersion")
                }
                if (latestRelease.body.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        RenderMarkdownContent(markdown = latestRelease.body)
                    }
                } else {
                    Text(stringResource(R.string.new_version_prompt_desc))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onNavigateToUpdate()
            }) {
                Text(stringResource(R.string.go_to_update))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onIgnore(latestRelease.versionName)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.ignore_this_version))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.later))
                }
            }
        }
    )
}
