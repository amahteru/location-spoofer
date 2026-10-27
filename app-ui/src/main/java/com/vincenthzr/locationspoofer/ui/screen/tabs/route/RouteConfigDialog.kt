package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.RouteRunMode
import com.vincenthzr.locationspoofer.data.model.SimMode
import com.vincenthzr.locationspoofer.ui.components.AppBottomSheet
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.listItemBleed
import com.vincenthzr.locationspoofer.ui.screen.AltitudeSettingsCard
import java.util.Locale
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun RouteConfigDialog(
    uiState: AppState,
    onDismiss: () -> Unit,
    onStartRoute: () -> Unit,
    onRunModeChange: (RouteRunMode) -> Unit,
    onSpeedChange: (SimMode) -> Unit,
    onCustomSpeedChange: (Double) -> Unit = {},
    onUseRealRouteChange: (Boolean) -> Unit = {},
    onStopAtDestinationChange: (Boolean) -> Unit = {},
    onToggleWifi: () -> Unit = {},
    onToggleCell: () -> Unit = {},
    onToggleBluetooth: () -> Unit = {},
    onToggleJitter: () -> Unit = {},
    onAltitudeChange: (String) -> Unit = {},
    onAltitudeVariationChange: (Int) -> Unit = {},
    onSatelliteCountChange: (String) -> Unit = {}
) {
    AppBottomSheet(
        title = stringResource(R.string.route_sim_settings),
        onDismiss = onDismiss,
        skipPartiallyExpanded = true
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = AppSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            SectionLabel(stringResource(R.string.run_mode))
            val runModes = listOf(
                Triple(RouteRunMode.LOOP, Icons.Rounded.Autorenew, R.string.loop_auto_cruise),
                Triple(RouteRunMode.MANUAL, Icons.Rounded.SportsEsports, R.string.joystick_manual_control)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                runModes.forEachIndexed { index, (mode, icon, label) ->
                    SegmentedButton(
                        selected = uiState.routeRunMode == mode,
                        onClick = { onRunModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, runModes.size),
                        icon = { SegmentedButtonDefaults.Icon(active = uiState.routeRunMode == mode) { Icon(icon, null, Modifier.size(18.dp)) } }
                    ) {
                        Text(stringResource(label), maxLines = 1)
                    }
                }
            }
            Text(
                text = stringResource(
                    if (uiState.routeRunMode == RouteRunMode.LOOP) R.string.loop_auto_cruise_desc else R.string.joystick_manual_control_desc
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel(stringResource(R.string.cruise_speed))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                listOf(
                    Triple(SimMode.WALKING, stringResource(R.string.walking), "1.4m/s"),
                    Triple(SimMode.RUNNING, stringResource(R.string.running), "3.0m/s"),
                    Triple(SimMode.CYCLING, stringResource(R.string.cycling), "5.5m/s"),
                    Triple(SimMode.DRIVING, stringResource(R.string.driving), "15m/s"),
                    Triple(SimMode.CUSTOM, stringResource(R.string.custom), "${String.format(Locale.US, "%.1f", uiState.customSpeedMs)}m/s")
                ).forEach { (mode, name, speed) ->
                    FilterChip(
                        selected = uiState.routeSimMode == mode,
                        onClick = { onSpeedChange(mode) },
                        label = { Text("$name · $speed") }
                    )
                }
            }

            if (uiState.routeSimMode == SimMode.CUSTOM) {
                var sliderValue by remember(uiState.customSpeedMs) {
                    mutableFloatStateOf(uiState.customSpeedMs.toFloat())
                }
                val paceSec = if (sliderValue > 0.2f) (1000.0 / sliderValue).toInt() else 0
                val paceStr = if (paceSec in 60..3599) "${paceSec / 60}'${String.format(Locale.US, "%02d", paceSec % 60)}\"/km" else "--"

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.custom_speed), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.pace_format, paceStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${String.format(Locale.US, "%.1f", sliderValue)} m/s (${String.format(Locale.US, "%.1f", sliderValue * 3.6)} km/h)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onCustomSpeedChange(it.toDouble())
                    },
                    valueRange = 0.2f..45.0f,
                    steps = 447
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    listOf(-1.0f, -0.2f, 0.2f, 1.0f).forEach { step ->
                        OutlinedButton(
                            onClick = {
                                val newVal = (sliderValue + step).coerceIn(0.2f, 45.0f)
                                sliderValue = newVal
                                onCustomSpeedChange(newVal.toDouble())
                            },
                            contentPadding = PaddingValues(horizontal = AppSpacing.xs),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (step > 0) "+$step" else "$step", maxLines = 1)
                        }
                    }
                }
            }

            HorizontalDivider()

            SwitchListItem(
                modifier = Modifier.listItemBleed(),
                title = stringResource(R.string.stop_at_destination_title),
                summary = stringResource(
                    if (uiState.stopAtDestination) R.string.stop_at_destination_desc_on else R.string.stop_at_destination_desc_off
                ),
                checked = uiState.stopAtDestination,
                onCheckedChange = onStopAtDestinationChange
            )

            AltitudeSettingsCard(
                altitudeInput = uiState.altitudeInput,
                variationM = uiState.altitudeVariationM,
                onAltitudeChange = onAltitudeChange,
                onVariationChange = onAltitudeVariationChange
            )

            SwitchListItem(
                modifier = Modifier.listItemBleed(),
                title = stringResource(R.string.real_road_matching),
                summary = stringResource(R.string.real_road_matching_desc),
                checked = uiState.useRealRoute,
                onCheckedChange = onUseRealRouteChange
            )

            HorizontalDivider()

            SectionLabel(stringResource(R.string.env_simulation_section))
            if (uiState.canMockWifi || uiState.wigleToken.isNotBlank()) SwitchListItem(
                modifier = Modifier.listItemBleed(),
                title = stringResource(R.string.mock_wifi_data),
                icon = Icons.Rounded.Wifi,
                checked = uiState.mockWifi,
                onCheckedChange = { onToggleWifi() }
            )
            if (uiState.canMockCell || uiState.opencellidToken.isNotBlank()) SwitchListItem(
                modifier = Modifier.listItemBleed(),
                title = stringResource(R.string.mock_cell_data),
                icon = Icons.Rounded.CellTower,
                checked = uiState.mockCell,
                onCheckedChange = { onToggleCell() }
            )
            if (uiState.canMockBluetooth) SwitchListItem(
                modifier = Modifier.listItemBleed(),
                title = stringResource(R.string.mock_bluetooth_data),
                icon = Icons.Rounded.Bluetooth,
                checked = uiState.mockBluetooth,
                onCheckedChange = { onToggleBluetooth() }
            )
            SwitchListItem(
                modifier = Modifier.listItemBleed(),
                title = stringResource(R.string.enable_slight_jitter),
                summary = stringResource(R.string.enable_slight_jitter_desc),
                icon = Icons.Rounded.GraphicEq,
                checked = uiState.enableJitter,
                onCheckedChange = { onToggleJitter() }
            )

            OutlinedTextField(
                value = uiState.satelliteCountInput,
                onValueChange = onSatelliteCountChange,
                label = { Text(stringResource(R.string.satellite_count)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Button(
            onClick = onStartRoute,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.screenMargin, end = AppSpacing.screenMargin, top = AppSpacing.lg)
                .height(48.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.start_route_simulation))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = AppSpacing.sm)
    )
}
