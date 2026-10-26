package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.listItemBleed
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun StartSpoofingDialog(
    uiState: AppState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onToggleWifi: () -> Unit,
    onToggleCell: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onToggleJitter: () -> Unit,
    onToggleRestartApps: () -> Unit,
    onAltitudeChange: (String) -> Unit,
    onAltitudeVariationChange: (Int) -> Unit,
    onSatelliteCountChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        icon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
        title = { Text(stringResource(R.string.spoofing_options_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = stringResource(R.string.spoofing_options_desc))
                Spacer(Modifier.height(AppSpacing.sm))

                // 两种方案都根据当前点位的数据能力显示选项；在线数据源可在开始模拟时补齐数据。
                val showWifiToggle = uiState.canMockWifi || uiState.wigleToken.isNotBlank()
                val showCellToggle = uiState.canMockCell || uiState.opencellidToken.isNotBlank()
                val showBluetoothToggle = uiState.canMockBluetooth

                if (showWifiToggle) {
                    SwitchListItem(
                        modifier = Modifier.listItemBleed(),
                        title = stringResource(R.string.mock_wifi_data),
                        icon = Icons.Outlined.Wifi,
                        checked = uiState.mockWifi,
                        onCheckedChange = { onToggleWifi() }
                    )
                }
                if (showCellToggle) {
                    SwitchListItem(
                        modifier = Modifier.listItemBleed(),
                        title = stringResource(R.string.mock_cell_data),
                        icon = Icons.Outlined.CellTower,
                        checked = uiState.mockCell,
                        onCheckedChange = { onToggleCell() }
                    )
                }
                if (showBluetoothToggle) {
                    SwitchListItem(
                        modifier = Modifier.listItemBleed(),
                        title = stringResource(R.string.mock_bluetooth_data),
                        icon = Icons.Outlined.Bluetooth,
                        checked = uiState.mockBluetooth,
                        onCheckedChange = { onToggleBluetooth() }
                    )
                }
                SwitchListItem(
                    modifier = Modifier.listItemBleed(),
                    title = stringResource(R.string.enable_slight_jitter),
                    icon = Icons.Outlined.GraphicEq,
                    checked = uiState.enableJitter,
                    onCheckedChange = { onToggleJitter() }
                )
                SwitchListItem(
                    modifier = Modifier.listItemBleed(),
                    title = stringResource(R.string.restart_apps_on_spoof),
                    icon = Icons.Outlined.RestartAlt,
                    checked = uiState.restartAppsOnSpoof,
                    onCheckedChange = { onToggleRestartApps() }
                )

                Spacer(Modifier.height(AppSpacing.sm))
                AltitudeSettingsCard(
                    altitudeInput = uiState.altitudeInput,
                    variationM = uiState.altitudeVariationM,
                    onAltitudeChange = onAltitudeChange,
                    onVariationChange = onAltitudeVariationChange
                )
                Spacer(Modifier.height(AppSpacing.sm))
                OutlinedTextField(
                    value = uiState.satelliteCountInput,
                    onValueChange = onSatelliteCountChange,
                    label = { Text(stringResource(R.string.satellite_count)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.start_simulation))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
