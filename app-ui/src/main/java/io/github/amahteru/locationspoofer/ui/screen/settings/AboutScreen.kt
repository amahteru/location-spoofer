package io.github.amahteru.locationspoofer.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.amahteru.locationspoofer.ui.BuildConfig
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.components.LabelTone
import io.github.amahteru.locationspoofer.ui.components.NavigationListItem
import io.github.amahteru.locationspoofer.ui.components.SubPageScaffold
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun AboutScreen(onOpenLicenses: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val appIcon = remember {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
    }

    SubPageScaffold(
        title = stringResource(R.string.about_title),
        onBack = onClose
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = AppSpacing.sm)
                )
                Text(
                    text = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.app_slogan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = AppSpacing.sm)) {
                NavigationListItem(
                    icon = ImageVector.vectorResource(R.drawable.ic_github),
                    title = stringResource(R.string.brand_github),
                    summary = stringResource(R.string.about_github_desc),
                    tone = LabelTone.Primary,
                    onClick = { uriHandler.openUri("https://github.com/amahteru/location-spoofer") },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) }
                )
                NavigationListItem(
                    icon = Icons.Rounded.Gavel,
                    title = stringResource(R.string.licenses_title),
                    summary = stringResource(R.string.about_licenses_desc),
                    tone = LabelTone.Tertiary,
                    onClick = onOpenLicenses
                )
            }
        }
    }
}
