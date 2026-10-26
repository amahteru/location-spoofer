package com.vincenthzr.locationspoofer.ui.screen.settings

import com.vincenthzr.locationspoofer.ui.theme.AppMotion
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.MapEngine
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.components.ModernSettingsInput
import com.vincenthzr.locationspoofer.ui.components.SectionTitle
import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.setAmapApiKey
import com.vincenthzr.locationspoofer.viewmodel.setBaiduApiKey
import com.vincenthzr.locationspoofer.viewmodel.setGoogleApiKey
import com.vincenthzr.locationspoofer.viewmodel.setKeepLastMapPosition
import com.vincenthzr.locationspoofer.viewmodel.setMapEngine
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun MapEngineSettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var localAmapApiKey by remember(uiState.amapApiKey) { mutableStateOf(uiState.amapApiKey) }
    var localBaiduApiKey by remember(uiState.baiduApiKey) { mutableStateOf(uiState.baiduApiKey) }
    var localGoogleApiKey by remember(uiState.googleApiKey) { mutableStateOf(uiState.googleApiKey) }

    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.map_config),
        subtitle = stringResource(R.string.map_engine_and_keys),
        onBack = onClose
    ) {
        // 地图引擎 4 分段选择器
        SectionTitle(stringResource(R.string.current_engine))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            val engines = listOf(
                MapEngine.AUTO to stringResource(R.string.engine_auto_short),
                MapEngine.AMAP to stringResource(R.string.engine_amap_short),
                MapEngine.BAIDU to stringResource(R.string.engine_baidu_short),
                MapEngine.GOOGLE to stringResource(R.string.engine_google_short)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                engines.forEachIndexed { index, (engine, label) ->
                    SegmentedButton(
                        selected = uiState.mapEngine == engine,
                        onClick = { viewModel.setMapEngine(engine) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = engines.size),
                        label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            // 各引擎自定义 API Key 输入
            AnimatedVisibility(
                visible = uiState.mapEngine == MapEngine.AMAP || uiState.mapEngine == MapEngine.BAIDU || uiState.mapEngine == MapEngine.GOOGLE,
                enter = fadeIn(AppMotion.defaultEffectsSpec()),
                exit = fadeOut(AppMotion.fastEffectsSpec())
            ) {
                Column(modifier = Modifier.padding(top = AppSpacing.lg)) {
                    when (uiState.mapEngine) {
                        MapEngine.AMAP -> {
                            ModernSettingsInput(
                                label = stringResource(R.string.custom_amap_key),
                                value = localAmapApiKey,
                                onValueChange = { localAmapApiKey = it },
                                placeholder = stringResource(R.string.custom_amap_key_hint)
                            )
                        }

                        MapEngine.BAIDU -> {
                            ModernSettingsInput(
                                label = stringResource(R.string.custom_baidu_key),
                                value = localBaiduApiKey,
                                onValueChange = { localBaiduApiKey = it },
                                placeholder = stringResource(R.string.custom_baidu_key_hint)
                            )
                        }

                        MapEngine.GOOGLE -> {
                            ModernSettingsInput(
                                label = stringResource(R.string.custom_google_key),
                                value = localGoogleApiKey,
                                onValueChange = { localGoogleApiKey = it },
                                placeholder = stringResource(R.string.custom_google_key_hint)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }

        Button(
            onClick = {
                viewModel.setAmapApiKey(localAmapApiKey)
                viewModel.setBaiduApiKey(localBaiduApiKey)
                viewModel.setGoogleApiKey(localGoogleApiKey)
                Toast.makeText(
                    context,
                    context.getString(R.string.restart_required_hint),
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchListItem(
                title = stringResource(R.string.keep_last_map_position_title),
                summary = stringResource(R.string.keep_last_map_position_desc),
                checked = uiState.keepLastMapPosition,
                onCheckedChange = { viewModel.setKeepLastMapPosition(it) }
            )
        }
    }
}
