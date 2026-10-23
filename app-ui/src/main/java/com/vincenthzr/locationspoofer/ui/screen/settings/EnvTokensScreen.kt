package com.vincenthzr.locationspoofer.ui.screen.settings

import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.components.ModernSettingsInput
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.setOpencellidApiToken
import com.vincenthzr.locationspoofer.viewmodel.setWigleApiToken
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.components.AppTopBar

@Composable
fun EnvTokensScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var localWigleToken by remember(uiState.wigleToken) { mutableStateOf(uiState.wigleToken) }
    var localOpencellidToken by remember(uiState.opencellidToken) { mutableStateOf(uiState.opencellidToken) }

    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.env_datasource_tokens_title),
        subtitle = stringResource(R.string.env_datasource_tokens_desc),
        onBack = onClose
    ) {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModernSettingsInput(
                    label = stringResource(R.string.custom_wigle_token),
                    value = localWigleToken,
                    onValueChange = { localWigleToken = it },
                    placeholder = stringResource(R.string.custom_wigle_token_hint)
                )

                ModernSettingsInput(
                    label = stringResource(R.string.custom_opencellid_token),
                    value = localOpencellidToken,
                    onValueChange = { localOpencellidToken = it },
                    placeholder = stringResource(R.string.custom_opencellid_token_hint)
                )
            }
        }

        Button(
            onClick = {
                viewModel.setWigleApiToken(localWigleToken)
                viewModel.setOpencellidApiToken(localOpencellidToken)
                Toast.makeText(
                    context,
                    context.getString(R.string.restart_required_hint),
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                stringResource(R.string.save),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
