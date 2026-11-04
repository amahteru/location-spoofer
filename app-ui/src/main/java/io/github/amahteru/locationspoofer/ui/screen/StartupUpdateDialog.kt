package io.github.amahteru.locationspoofer.ui.screen

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.amahteru.locationspoofer.ui.BuildConfig
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.viewmodel.LatestBuild
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StartupUpdateDialog(
    latestBuild: LatestBuild,
    onDismiss: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onIgnore: (String) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val builtAt = remember(latestBuild.createdAt, locale) { formatBuildTime(latestBuild.createdAt, locale) }
    val lines = listOfNotNull(
        stringResource(R.string.build_ready_format, latestBuild.shortSha),
        stringResource(R.string.current_version_format, BuildConfig.VERSION_NAME),
        builtAt?.let { stringResource(R.string.built_at_format, it) }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.new_version_found)) },
        text = { Text(lines.joinToString("\n")) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onNavigateToUpdate()
            }) {
                Text(stringResource(R.string.view))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onIgnore(latestBuild.sha)
                onDismiss()
            }) {
                Text(stringResource(R.string.ignore))
            }
        }
    )
}

private fun formatBuildTime(isoTime: String, locale: Locale): String? = runCatching {
    val pattern = DateFormat.getBestDateTimePattern(locale, "MMMdHHmm")
    DateTimeFormatter.ofPattern(pattern, locale).format(Instant.parse(isoTime).atZone(ZoneId.systemDefault()))
}.getOrNull()
