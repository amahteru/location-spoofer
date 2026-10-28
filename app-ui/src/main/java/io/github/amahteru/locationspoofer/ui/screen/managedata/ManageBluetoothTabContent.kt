package io.github.amahteru.locationspoofer.ui.screen.managedata

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.components.LabelTone

@Composable
fun ManageBluetoothTabContent(
    btList: List<EditableBluetoothItem>,
    selectedBluetoothAddress: String?,
    onSelectAddress: (String?) -> Unit,
    onAddBluetooth: () -> Unit,
    onEditBluetooth: (EditableBluetoothItem) -> Unit,
    onDeleteBluetooth: (String) -> Unit
) {
    val designatedLabel = stringResource(R.string.designated_bt_badge)
    DesignatedDeviceList(
        title = stringResource(R.string.manage_bt_list_title),
        addLabel = stringResource(R.string.add_bt_btn),
        tip = stringResource(R.string.designated_bt_tip),
        autoLabel = stringResource(R.string.no_designated_bt),
        emptyText = stringResource(R.string.no_bt_data_in_record),
        items = btList,
        isSelected = { selectedBluetoothAddress?.equals(it.address, ignoreCase = true) == true },
        headline = { it.name.ifBlank { "<Unknown Device>" } },
        badges = { bt ->
            if (selectedBluetoothAddress?.equals(bt.address, ignoreCase = true) == true) {
                listOf(DeviceBadge(designatedLabel, LabelTone.Primary))
            } else emptyList()
        },
        details = { bt ->
            listOfNotNull(
                "${bt.address}  •  ${bt.rssi} dBm",
                bt.scanRecordHex.takeIf { it.isNotBlank() }?.let { "Hex: $it" }
            )
        },
        onSelectAuto = { onSelectAddress(null) },
        onSelect = { onSelectAddress(it.address) },
        onAdd = onAddBluetooth,
        onEdit = onEditBluetooth,
        onDelete = { onDeleteBluetooth(it.address) }
    )
}
