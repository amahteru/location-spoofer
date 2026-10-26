package com.vincenthzr.locationspoofer.ui.screen.settings

import androidx.core.net.toUri
import com.vincenthzr.locationspoofer.ui.components.NavigationListItem
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material3.Card
import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.components.AppTopBar
import com.vincenthzr.locationspoofer.ui.theme.warningContainer
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

// 本页就是引导用户关闭电池优化的保活设置页，且应用不上架 Play
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
                // 忽略电池优化
                NavigationListItem(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = stringResource(R.string.ignore_battery_optimizations_title),
                    summary = stringResource(R.string.ignore_battery_optimizations_desc),
                    tone = LabelTone.Warning,
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
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    resources.getString(R.string.open_battery_opt_failed, e.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                // 允许应用自启动与后台活动
                NavigationListItem(
                    icon = Icons.Rounded.AppSettingsAlt,
                    title = stringResource(R.string.app_info_settings_title),
                    summary = stringResource(R.string.app_info_settings_desc),
                    tone = LabelTone.Primary,
                    onClick = {
                    try {
                        val intent =
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .apply {
                                    data =
                                        "package:${context.packageName}".toUri()
                                }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            resources.getString(R.string.open_app_info_failed, e.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    }
                )
            }
        }
    }
}
