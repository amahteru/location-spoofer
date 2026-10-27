package com.vincenthzr.locationspoofer.ui.screen.managedata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.listItemBleed
import java.util.Locale
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun EditBluetoothDialog(
    initialItem: EditableBluetoothItem?,
    onDismiss: () -> Unit,
    onSave: (
        address: String,
        name: String,
        scanRecordHex: String,
        rssi: Int,
        isDesignated: Boolean
    ) -> Unit
) {
    val isEdit = initialItem != null
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var address by remember { mutableStateOf(initialItem?.address ?: "") }
    var rssi by remember { mutableIntStateOf(initialItem?.rssi ?: -60) }
    var scanRecordHex by remember { mutableStateOf(initialItem?.scanRecordHex ?: "") }
    var isDesignated by remember { mutableStateOf(initialItem?.isDesignated ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bluetooth, contentDescription = null) },
        title = { Text(stringResource(if (isEdit) R.string.edit_bt_title else R.string.new_bt_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.bt_name_label)) },
                    placeholder = { Text(stringResource(R.string.bt_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.uppercase() },
                    label = { Text(stringResource(R.string.bt_address_label)) },
                    placeholder = { Text(stringResource(R.string.bt_address_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.bt_rssi_label, rssi),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val options = listOf(-45 to "-45", -60 to "-60", -75 to "-75", -90 to "-90")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (lvl, lbl) ->
                        SegmentedButton(
                            selected = rssi == lvl,
                            onClick = { rssi = lvl },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            icon = {},
                            label = { Text(text = lbl, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                OutlinedTextField(
                    value = scanRecordHex,
                    onValueChange = { scanRecordHex = it },
                    label = { Text(stringResource(R.string.bt_scan_record_label)) },
                    placeholder = { Text(stringResource(R.string.bt_scan_record_hint)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                SwitchListItem(
                    modifier = Modifier.listItemBleed(),
                    title = stringResource(R.string.designated_bt_badge),
                    checked = isDesignated,
                    onCheckedChange = { isDesignated = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalAddress = address.ifBlank {
                    String.format(Locale.US, "02:00:00:%02X:%02X:%02X", (0..255).random(), (0..255).random(), (0..255).random())
                }
                val finalName = name.ifBlank { "Mock_BLE" }
                onSave(
                    finalAddress,
                    finalName,
                    scanRecordHex,
                    rssi,
                    isDesignated
                )
            }) {
                Text(stringResource(R.string.save_bt))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
