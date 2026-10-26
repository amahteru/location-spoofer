package com.vincenthzr.locationspoofer.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.RootSetupTestResult
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.theme.success
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
private fun DiagnosticItemRow(label: String, ok: Boolean, detail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RootSetupTestResultDialog(
    result: RootSetupTestResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboardManager = LocalClipboardManager.current
    val overallOk = result.hasRoot && result.overallVerified

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (overallOk) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                contentDescription = null,
                tint = if (overallOk) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.root_test_result_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                DiagnosticItemRow(
                    label = stringResource(R.string.root_test_item_root),
                    ok = result.hasRoot,
                    detail = result.idOutput.take(120)
                )
                DiagnosticItemRow(
                    label = stringResource(R.string.root_test_item_framework),
                    ok = result.frameworkConnected
                )
                DiagnosticItemRow(
                    label = stringResource(R.string.root_test_item_remote_config),
                    ok = result.remoteConfigAccessible,
                    detail = stringResource(R.string.root_test_remote_config_detail)
                )
                DiagnosticItemRow(
                    label = stringResource(R.string.root_test_item_config_published),
                    ok = result.configPublished,
                    detail = if (result.configPublished) null else stringResource(R.string.root_test_config_not_published)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val summary = buildString {
                    appendLine("solution=${result.solution}")
                    appendLine("hasRoot=${result.hasRoot}")
                    appendLine("idOutput=${result.idOutput}")
                    appendLine("frameworkConnected=${result.frameworkConnected}")
                    appendLine("remoteConfigAccessible=${result.remoteConfigAccessible}")
                    appendLine("configPublished=${result.configPublished}")
                    appendLine("frameworkDetail=${result.frameworkDetail}")
                }
                clipboardManager.setText(AnnotatedString(summary))
                Toast.makeText(
                    context,
                    resources.getString(R.string.copied_to_clipboard),
                    Toast.LENGTH_SHORT
                ).show()
            }) {
                Text(stringResource(R.string.copy_diagnostic_info))
            }
        }
    )
}

@Composable
fun RestartHookedAppsConfirmDialog(
    apps: List<com.vincenthzr.locationspoofer.data.model.AppInfoItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
        title = { Text(stringResource(R.string.restart_hooked_apps_confirm_title)) },
        text = {
            if (apps.isEmpty()) {
                Text(stringResource(R.string.restart_hooked_apps_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(stringResource(R.string.restart_hooked_apps_confirm_message, apps.size))
                    Text(
                        text = apps.joinToString("、") { it.appName },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (apps.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            } else {
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.restart_hooked_apps_confirm_button))
                }
            }
        },
        dismissButton = if (apps.isEmpty()) null else {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
