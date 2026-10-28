package io.github.amahteru.locationspoofer.ui.screen.settings

import io.github.amahteru.locationspoofer.ui.components.listItemBleed
import io.github.amahteru.locationspoofer.ui.components.SwitchListItem
import io.github.amahteru.locationspoofer.ui.components.ToneIcon
import io.github.amahteru.locationspoofer.ui.components.LabelTone
import io.github.amahteru.locationspoofer.ui.components.SubPageScaffold
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
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.utils.MotionRealism
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.setRealismLevel
import io.github.amahteru.locationspoofer.viewmodel.setSpeedFluctuationPct
import io.github.amahteru.locationspoofer.viewmodel.setStepSimulationEnabled
import kotlin.math.roundToInt
import io.github.amahteru.locationspoofer.ui.components.AppCard
import io.github.amahteru.locationspoofer.ui.components.AppTopBar
import androidx.compose.ui.text.style.TextOverflow
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun MotionRealismScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.motion_realism_title),
        subtitle = stringResource(R.string.motion_realism_desc),
        onBack = onClose
    ) {
        RealismLevelCard(uiState) { viewModel.setRealismLevel(it) }
        SpeedFluctuationCard(uiState) { viewModel.setSpeedFluctuationPct(it) }
        HintRow(stringResource(R.string.realism_next_session_hint))
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchListItem(
                title = stringResource(R.string.step_simulation_title),
                summary = stringResource(R.string.step_simulation_desc),
                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                checked = uiState.stepSimulationEnabled,
                onCheckedChange = { viewModel.setStepSimulationEnabled(it) }
            )
        }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, tone: LabelTone, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToneIcon(icon, tone)
        Spacer(Modifier.width(AppSpacing.lg))
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
    AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppSpacing.lg)) {
        CardHeader(
            Icons.Rounded.Shuffle,
            LabelTone.Primary,
            stringResource(R.string.realism_level_title),
            stringResource(R.string.realism_level_desc)
        )
        Spacer(Modifier.height(AppSpacing.lg))
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
    AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppSpacing.lg)) {
        CardHeader(
            Icons.Rounded.Speed,
            LabelTone.Warning,
            stringResource(R.string.speed_fluctuation_title),
            stringResource(R.string.speed_fluctuation_desc)
        )
        Spacer(Modifier.height(AppSpacing.sm))
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
            Spacer(Modifier.width(AppSpacing.md))
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
private fun HintRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Rounded.Info,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
