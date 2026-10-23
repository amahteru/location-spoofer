package com.vincenthzr.locationspoofer.ui.screen.settings

import com.vincenthzr.locationspoofer.ui.components.listItemBleed
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.ToneIcon
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.GaitRecordingState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.utils.GaitTemplate
import com.vincenthzr.locationspoofer.utils.MotionRealism
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.cancelGaitRecording
import com.vincenthzr.locationspoofer.viewmodel.deleteGaitTemplate
import com.vincenthzr.locationspoofer.viewmodel.dismissGaitRecordingResult
import com.vincenthzr.locationspoofer.viewmodel.setRealismLevel
import com.vincenthzr.locationspoofer.viewmodel.setSpeedFluctuationPct
import com.vincenthzr.locationspoofer.viewmodel.setUseGaitTemplate
import com.vincenthzr.locationspoofer.viewmodel.startGaitRecording
import kotlin.math.roundToInt
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.components.AppTopBar
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun MotionRealismScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    // 录制时手机放进口袋，保持屏幕常亮，避免熄屏后传感器数据中断
    val recording = uiState.gaitRecording
    val keepAwake = recording is GaitRecordingState.Countdown || recording is GaitRecordingState.Recording
    val view = LocalView.current
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    SubPageScaffold(
        title = stringResource(R.string.motion_realism_title),
        subtitle = stringResource(R.string.motion_realism_desc),
        onBack = onClose
    ) {
        RealismLevelCard(uiState) { viewModel.setRealismLevel(it) }
        SpeedFluctuationCard(uiState) { viewModel.setSpeedFluctuationPct(it) }
        GaitCard(viewModel, uiState)
        HintRow(stringResource(R.string.realism_next_session_hint))
    }
}

@Composable
private fun CardHeader(icon: ImageVector, tone: LabelTone, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToneIcon(icon, tone)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RealismLevelCard(uiState: AppState, onSelect: (Int) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        CardHeader(
            Icons.Rounded.Shuffle,
            LabelTone.Primary,
            stringResource(R.string.realism_level_title),
            stringResource(R.string.realism_level_desc)
        )
        Spacer(Modifier.height(14.dp))
        val levels = listOf(
            MotionRealism.Level.OFF to stringResource(R.string.realism_level_off),
            MotionRealism.Level.LOW to stringResource(R.string.realism_level_low),
            MotionRealism.Level.MEDIUM to stringResource(R.string.realism_level_medium),
            MotionRealism.Level.HIGH to stringResource(R.string.realism_level_high)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            levels.forEachIndexed { index, (level, label) ->
                SegmentedButton(
                    selected = uiState.realismLevel == level.id,
                    onClick = { onSelect(level.id) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
                    label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

@Composable
private fun SpeedFluctuationCard(uiState: AppState, onChange: (Int) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        CardHeader(
            Icons.Rounded.Speed,
            LabelTone.Warning,
            stringResource(R.string.speed_fluctuation_title),
            stringResource(R.string.speed_fluctuation_desc)
        )
        Spacer(Modifier.height(8.dp))
        var local by remember(uiState.speedFluctuationPct) { mutableFloatStateOf(uiState.speedFluctuationPct.toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onChange(local.roundToInt()) },
                valueRange = 0f..MotionRealism.MAX_SPEED_FLUCTUATION_PCT.toFloat(),
                steps = MotionRealism.MAX_SPEED_FLUCTUATION_PCT / 5 - 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.speed_fluctuation_value, local.roundToInt()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 48.dp)
            )
        }
    }
}

@Composable
private fun GaitCard(viewModel: MainViewModel, uiState: AppState) {
    AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        CardHeader(
            Icons.AutoMirrored.Rounded.DirectionsWalk,
            LabelTone.Success,
            stringResource(R.string.gait_title),
            stringResource(R.string.gait_desc)
        )
        Spacer(Modifier.height(12.dp))

        when (val recording = uiState.gaitRecording) {
            is GaitRecordingState.Countdown, is GaitRecordingState.Recording, GaitRecordingState.Processing -> {
                val text = when (recording) {
                    is GaitRecordingState.Countdown -> stringResource(R.string.gait_countdown_format, recording.secondsLeft)
                    is GaitRecordingState.Recording -> stringResource(R.string.gait_recording)
                    else -> stringResource(R.string.gait_processing)
                }
                Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                if (recording is GaitRecordingState.Recording) {
                    LinearProgressIndicator(
                        progress = { recording.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (recording !is GaitRecordingState.Processing) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { viewModel.cancelGaitRecording() }) {
                        Text(stringResource(R.string.gait_cancel))
                    }
                }
            }

            is GaitRecordingState.Failed -> {
                val reason = when (recording.reason) {
                    GaitTemplate.Reason.TOO_SHORT -> stringResource(R.string.gait_failed_too_short)
                    GaitTemplate.Reason.NO_RHYTHM -> stringResource(R.string.gait_failed_no_rhythm)
                    GaitTemplate.Reason.TOO_FEW_STRIDES -> stringResource(R.string.gait_failed_too_few_strides)
                }
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.startGaitRecording() },
                                            ) { Text(stringResource(R.string.gait_retry)) }
                    OutlinedButton(onClick = { viewModel.dismissGaitRecordingResult() }) {
                        Text(stringResource(R.string.gait_cancel))
                    }
                }
            }

            GaitRecordingState.Idle -> {
                val cadence = uiState.gaitTemplateCadence
                Text(
                    text = if (cadence != null) {
                        stringResource(R.string.gait_recorded_format, cadence, uiState.gaitTemplateStrides)
                    } else {
                        stringResource(R.string.gait_not_recorded)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cadence != null) {
                    Spacer(Modifier.height(6.dp))
                    SwitchListItem(
                        modifier = Modifier.listItemBleed(),
                        title = stringResource(R.string.gait_use_template),
                        checked = uiState.useGaitTemplate,
                        onCheckedChange = { viewModel.setUseGaitTemplate(it) }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.startGaitRecording() }
                    ) {
                        Text(stringResource(if (cadence != null) R.string.gait_rerecord else R.string.gait_record))
                    }
                    if (cadence != null) {
                        OutlinedButton(onClick = { viewModel.deleteGaitTemplate() }) {
                            Text(stringResource(R.string.gait_delete))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HintRow(stringResource(R.string.gait_scope_hint))
    }
}

@Composable
private fun HintRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Rounded.Info,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
