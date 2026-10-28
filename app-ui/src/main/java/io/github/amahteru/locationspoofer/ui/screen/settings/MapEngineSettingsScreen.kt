package io.github.amahteru.locationspoofer.ui.screen.settings

import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.data.model.MapEngine
import io.github.amahteru.locationspoofer.ui.components.AppCard
import io.github.amahteru.locationspoofer.ui.components.ModernSettingsInput
import io.github.amahteru.locationspoofer.ui.components.SectionTitle
import io.github.amahteru.locationspoofer.ui.components.SubPageScaffold
import io.github.amahteru.locationspoofer.ui.components.SwitchListItem
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.setAmapApiKey
import io.github.amahteru.locationspoofer.viewmodel.setBaiduApiKey
import io.github.amahteru.locationspoofer.viewmodel.setGoogleApiKey
import io.github.amahteru.locationspoofer.viewmodel.setKeepLastMapPosition
import io.github.amahteru.locationspoofer.viewmodel.setMapEngine
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun MapEngineSettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var localAmapApiKey by remember(uiState.amapApiKey) { mutableStateOf(uiState.amapApiKey) }
    var localBaiduApiKey by remember(uiState.baiduApiKey) { mutableStateOf(uiState.baiduApiKey) }
    var localGoogleApiKey by remember(uiState.googleApiKey) { mutableStateOf(uiState.googleApiKey) }

    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.map_config),
        subtitle = stringResource(R.string.map_engine_and_keys),
        onBack = onClose
    ) {
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

            val lastKeyEngine = remember { arrayOf(uiState.mapEngine) }
            if (uiState.mapEngine != MapEngine.AUTO) lastKeyEngine[0] = uiState.mapEngine
            val keyEngine = lastKeyEngine[0]
            AnimatedVisibility(
                visible = uiState.mapEngine != MapEngine.AUTO,
                enter = fadeIn(AppMotion.defaultEffectsSpec()) + expandVertically(AppMotion.defaultSpatialSpec()),
                exit = fadeOut(AppMotion.fastEffectsSpec()) + shrinkVertically(AppMotion.defaultSpatialSpec())
            ) {
                val (label, hint) = when (keyEngine) {
                    MapEngine.BAIDU -> R.string.custom_baidu_key to R.string.custom_baidu_key_hint
                    MapEngine.GOOGLE -> R.string.custom_google_key to R.string.custom_google_key_hint
                    else -> R.string.custom_amap_key to R.string.custom_amap_key_hint
                }
                ModernSettingsInput(
                    modifier = Modifier.padding(top = AppSpacing.lg),
                    label = stringResource(label),
                    value = when (keyEngine) {
                        MapEngine.BAIDU -> localBaiduApiKey
                        MapEngine.GOOGLE -> localGoogleApiKey
                        else -> localAmapApiKey
                    },
                    onValueChange = { value ->
                        when (keyEngine) {
                            MapEngine.BAIDU -> localBaiduApiKey = value
                            MapEngine.GOOGLE -> localGoogleApiKey = value
                            else -> localAmapApiKey = value
                        }
                    },
                    placeholder = stringResource(hint)
                )
            }
        }

        Button(
            onClick = {
                viewModel.setAmapApiKey(localAmapApiKey)
                viewModel.setBaiduApiKey(localBaiduApiKey)
                viewModel.setGoogleApiKey(localGoogleApiKey)
                Toast.makeText(
                    context,
                    resources.getString(R.string.restart_required_hint),
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
