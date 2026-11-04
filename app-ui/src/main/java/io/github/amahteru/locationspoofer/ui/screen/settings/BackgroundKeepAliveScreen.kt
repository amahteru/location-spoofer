package io.github.amahteru.locationspoofer.ui.screen.settings

import io.github.amahteru.locationspoofer.ui.components.AppSnackbar
import androidx.core.net.toUri
import io.github.amahteru.locationspoofer.ui.components.NavigationListItem
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material3.Card
import io.github.amahteru.locationspoofer.ui.components.SubPageScaffold
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@android.annotation.SuppressLint("BatteryLife")
@Composable
fun BackgroundKeepAliveScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.background_keep_alive_title),
        subtitle = stringResource(R.string.background_keep_alive_desc),
        onBack = onClose
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = AppSpacing.sm)) {
                NavigationListItem(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = stringResource(R.string.ignore_battery_optimizations_title),
                    summary = stringResource(R.string.ignore_battery_optimizations_desc),
                    onClick = {
                        try {
                            val intent =
                                android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .apply {
                                        data =
                                            "package:${context.packageName}".toUri()
                                    }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent =
                                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                AppSnackbar.show(resources.getString(R.string.open_battery_opt_failed), long = true)
                            }
                        }
                    }
                )
                NavigationListItem(
                    icon = Icons.Rounded.AppSettingsAlt,
                    title = stringResource(R.string.app_info_settings_title),
                    summary = stringResource(R.string.app_info_settings_desc),
                    onClick = {
                    try {
                        val intent =
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .apply {
                                    data =
                                        "package:${context.packageName}".toUri()
                                }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        AppSnackbar.show(resources.getString(R.string.open_app_info_failed), long = true)
                    }
                    }
                )
            }
        }
    }
}
