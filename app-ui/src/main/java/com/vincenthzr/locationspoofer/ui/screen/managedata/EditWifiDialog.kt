package com.vincenthzr.locationspoofer.ui.screen.managedata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.listItemBleed
import java.util.Locale

@Composable
fun EditWifiDialog(
    initialItem: EditableWifiItem?,
    onDismiss: () -> Unit,
    onSave: (
        bssid: String,
        ssid: String,
        frequency: Int,
        level: Int,
        capabilities: String,
        vendor: String,
        isConnected: Boolean,
        isDesignated: Boolean
    ) -> Unit
) {
    val isEdit = initialItem != null
    var ssid by remember { mutableStateOf(initialItem?.ssid ?: "") }
    var bssid by remember { mutableStateOf(initialItem?.bssid ?: "") }
    var level by remember { mutableIntStateOf(initialItem?.level ?: -55) }
    var frequency by remember { mutableIntStateOf(initialItem?.frequency ?: 5180) }
    var capabilities by remember { mutableStateOf(initialItem?.capabilities ?: "[WPA2-PSK-CCMP][RSN]") }
    var vendor by remember { mutableStateOf(initialItem?.vendor ?: "") }
    var isConnected by remember { mutableStateOf(initialItem?.isConnected ?: false) }
    var isDesignated by remember { mutableStateOf(initialItem?.isDesignated ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
        title = { Text(stringResource(if (isEdit) R.string.edit_wifi_title else R.string.new_wifi_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text(stringResource(R.string.wifi_ssid_label)) },
                    placeholder = { Text(stringResource(R.string.wifi_ssid_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bssid,
                    onValueChange = { bssid = it.uppercase() },
                    label = { Text(stringResource(R.string.wifi_bssid_label)) },
                    placeholder = { Text(stringResource(R.string.wifi_bssid_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 信号强度
                Text(
                    text = stringResource(R.string.wifi_level_label, level),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val levelOptions = listOf(-40 to "-40", -55 to "-55", -70 to "-70", -85 to "-85")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    levelOptions.forEachIndexed { index, (v, lbl) ->
                        SegmentedButton(
                            selected = level == v,
                            onClick = { level = v },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = levelOptions.size),
                            icon = {},
                            label = { Text(text = lbl, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                // 工作频率
                Text(
                    text = stringResource(R.string.wifi_freq_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val freqOptions = listOf(2412 to "2.4G", 5180 to "5G", 5745 to "5.8G")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    freqOptions.forEachIndexed { index, (v, lbl) ->
                        SegmentedButton(
                            selected = frequency == v,
                            onClick = { frequency = v },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = freqOptions.size),
                            icon = {},
                            label = { Text(text = lbl, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                // 加密属性：可手动输入，也可点选常用预设
                OutlinedTextField(
                    value = capabilities,
                    onValueChange = { capabilities = it },
                    label = { Text(stringResource(R.string.wifi_capabilities_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("[WPA2-PSK-CCMP][RSN]" to "WPA2", "[WPA3-SAE]" to "WPA3", "[ESS]" to "OPEN").forEach { (cap, tag) ->
                        FilterChip(
                            selected = capabilities == cap,
                            onClick = { capabilities = cap },
                            label = { Text(tag) }
                        )
                    }
                }

                SwitchListItem(
                    modifier = Modifier.listItemBleed(),
                    title = stringResource(R.string.wifi_is_connected_label),
                    checked = isConnected,
                    onCheckedChange = { isConnected = it }
                )
                SwitchListItem(
                    modifier = Modifier.listItemBleed(),
                    title = stringResource(R.string.designated_simulation_badge),
                    checked = isDesignated,
                    onCheckedChange = { isDesignated = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalBssid = bssid.ifBlank {
                    String.format(Locale.US, "02:00:00:%02X:%02X:%02X", (0..255).random(), (0..255).random(), (0..255).random())
                }
                val finalSsid = ssid.ifBlank { "Mock_WiFi" }
                onSave(
                    finalBssid,
                    finalSsid,
                    frequency,
                    level,
                    capabilities,
                    vendor,
                    isConnected,
                    isDesignated
                )
            }) {
                Text(stringResource(R.string.save_wifi))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
