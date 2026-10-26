package com.vincenthzr.locationspoofer.ui.screen.managedata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.SwitchListItem
import com.vincenthzr.locationspoofer.ui.components.listItemBleed
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun EditCellDialog(
    initialItem: EditableCellItem?,
    onDismiss: () -> Unit,
    onSave: (
        cellKey: String,
        type: String,
        mcc: Int,
        mnc: Int,
        tac: Int,
        ci: Int,
        pci: Int,
        lac: Int,
        cid: Int,
        psc: Int,
        nci: Long,
        networkId: Int,
        systemId: Int,
        basestationId: Int,
        dbm: Int,
        isRegistered: Boolean,
        isDesignated: Boolean
    ) -> Unit
) {
    val isEdit = initialItem != null
    var type by remember { mutableStateOf(initialItem?.type?.uppercase() ?: "LTE") }
    var mccStr by remember { mutableStateOf(initialItem?.mcc?.toString() ?: "460") }
    var mncStr by remember { mutableStateOf(initialItem?.mnc?.toString() ?: "0") }
    
    var tacStr by remember { mutableStateOf(if (initialItem != null) (if (initialItem.tac != 0) initialItem.tac else initialItem.lac).toString() else "28854") }
    var ciStr by remember { mutableStateOf(if (initialItem != null) (if (initialItem.ci != 0) initialItem.ci else initialItem.cid).toString() else "1234567") }
    var pciStr by remember { mutableStateOf(if (initialItem != null) (if (initialItem.pci != 0) initialItem.pci else initialItem.psc).toString() else "123") }
    
    var dbm by remember { mutableIntStateOf(initialItem?.dbm ?: -85) }
    var isRegistered by remember { mutableStateOf(initialItem?.isRegistered ?: true) }
    var isDesignated by remember { mutableStateOf(initialItem?.isDesignated ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CellTower, contentDescription = null) },
        title = { Text(stringResource(if (isEdit) R.string.edit_cell_title else R.string.new_cell_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                // 制式选择
                Text(
                    text = stringResource(R.string.cell_type_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val types = listOf("LTE", "NR", "GSM", "WCDMA")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = type.equals(t, ignoreCase = true),
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                            icon = {},
                            label = { Text(text = t, maxLines = 1) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    OutlinedTextField(
                    value = mccStr,
                    onValueChange = { mccStr = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.cell_mcc_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                    OutlinedTextField(
                    value = mncStr,
                    onValueChange = { mncStr = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.cell_mnc_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                }
                OutlinedTextField(
                    value = tacStr,
                    onValueChange = { tacStr = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.cell_tac_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    OutlinedTextField(
                    value = ciStr,
                    onValueChange = { ciStr = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.cell_ci_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1.2f)
                )
                    OutlinedTextField(
                    value = pciStr,
                    onValueChange = { pciStr = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.cell_pci_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.8f)
                )
                }

                // 信号强度
                Text(
                    text = stringResource(R.string.cell_dbm_label, dbm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val options = listOf(-65 to "-65", -80 to "-80", -95 to "-95", -110 to "-110")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (lvl, lbl) ->
                        SegmentedButton(
                            selected = dbm == lvl,
                            onClick = { dbm = lvl },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            icon = {},
                            label = { Text(text = lbl, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                SwitchListItem(
                    modifier = Modifier.listItemBleed(),
                    title = stringResource(R.string.cell_is_registered_label),
                    checked = isRegistered,
                    onCheckedChange = { isRegistered = it }
                )
                SwitchListItem(
                    modifier = Modifier.listItemBleed(),
                    title = stringResource(R.string.designated_cell_badge),
                    checked = isDesignated,
                    onCheckedChange = { isDesignated = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mcc = mccStr.toIntOrNull() ?: 460
                val mnc = mncStr.toIntOrNull() ?: 0
                val tac = tacStr.toIntOrNull() ?: 0
                val ci = ciStr.toIntOrNull() ?: 0
                val pci = pciStr.toIntOrNull() ?: 0
                val cellKey = initialItem?.cellKey ?: "${type}_${mcc}_${mnc}_${tac}_${ci}"
                            
                val lac = if (type == "GSM" || type == "WCDMA") tac else 0
                val cid = if (type == "GSM" || type == "WCDMA") ci else 0
                val psc = if (type == "WCDMA") pci else 0
                val nci = if (type == "NR") ci.toLong() else 0L

                onSave(
                    cellKey,
                    type,
                    mcc,
                    mnc,
                    tac,
                    ci,
                    pci,
                    lac,
                    cid,
                    psc,
                    nci,
                    0,
                    0,
                    0,
                    dbm,
                    isRegistered,
                    isDesignated
                )
            }) {
                Text(stringResource(R.string.save_cell))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
