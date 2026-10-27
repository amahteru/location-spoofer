package com.vincenthzr.locationspoofer.ui.screen.settings

import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.Card
import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.components.AppTopBar
import com.vincenthzr.locationspoofer.ui.theme.warning
import androidx.compose.foundation.clickable
import com.vincenthzr.locationspoofer.ui.theme.warningContainer
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun SignatureAuthScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboardManager = LocalClipboardManager.current

    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.env_and_signature),
        subtitle = stringResource(R.string.api_key_binding_desc),
        onBack = onClose
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = AppSpacing.sm)) {
                CopyableListItem(
                    label = stringResource(R.string.app_package_name),
                    value = context.packageName,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(context.packageName))
                        Toast.makeText(context, resources.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }
                )
                CopyableListItem(
                    label = stringResource(R.string.app_sha1),
                    value = uiState.appSha1.ifBlank { stringResource(R.string.reading_signature) },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(uiState.appSha1))
                        Toast.makeText(context, resources.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun CopyableListItem(label: String, value: String, onCopy: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onCopy),
        overlineContent = { Text(label) },
        headlineContent = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
