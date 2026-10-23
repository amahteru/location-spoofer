package com.vincenthzr.locationspoofer.ui.screen.managedata

import com.vincenthzr.locationspoofer.ui.components.LabelTone
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.db.CompleteLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernEditDataDialog(
    item: CompleteLocation,
    onDismiss: () -> Unit,
    onSave: (lat: Double, lng: Double, placeName: String, remark: String, selectedWifiBssid: String?, selectedBluetoothAddress: String?, selectedCellKey: String?) -> Unit,
    onSaveWifi: (bssid: String, ssid: String, frequency: Int, level: Int, capabilities: String, vendor: String, isConnected: Boolean, isDesignated: Boolean) -> Unit,
    onDeleteWifi: (bssid: String) -> Unit,
    onSaveCell: (cellKey: String, type: String, mcc: Int, mnc: Int, tac: Int, ci: Int, pci: Int, lac: Int, cid: Int, psc: Int, nci: Long, networkId: Int, systemId: Int, basestationId: Int, dbm: Int, isRegistered: Boolean, isDesignated: Boolean) -> Unit,
    onDeleteCell: (cellKey: String) -> Unit,
    onSaveBluetooth: (address: String, name: String, scanRecordHex: String, rssi: Int, isDesignated: Boolean) -> Unit,
    onDeleteBluetooth: (address: String) -> Unit
) {
    var latText by remember(item.location.id) {
        mutableStateOf(String.format(Locale.US, "%.6f", item.location.lat))
    }
    var lngText by remember(item.location.id) {
        mutableStateOf(String.format(Locale.US, "%.6f", item.location.lng))
    }
    var placeName by remember(item.location.id) { mutableStateOf(item.location.placeName) }
    var remark by remember(item.location.id) { mutableStateOf(item.location.remark) }
    var selectedWifiBssid by remember(item.location.id, item.location.selectedWifiBssid) {
        mutableStateOf(item.location.selectedWifiBssid)
    }
    var selectedBluetoothAddress by remember(item.location.id, item.location.selectedBluetoothAddress) {
        mutableStateOf(item.location.selectedBluetoothAddress)
    }
    var selectedCellKey by remember(item.location.id, item.location.selectedCellKey) {
        mutableStateOf(item.location.selectedCellKey)
    }

    var selectedTab by remember { mutableStateOf(EditDataTab.WIFI) }

    // Wi-Fi 弹窗状态
    var wifiBeingEdited by remember { mutableStateOf<EditableWifiItem?>(null) }
    var isNewWifiDialog by remember { mutableStateOf(false) }
    var wifiToDelete by remember { mutableStateOf<String?>(null) }

    // 基站弹窗状态
    var cellBeingEdited by remember { mutableStateOf<EditableCellItem?>(null) }
    var isNewCellDialog by remember { mutableStateOf(false) }
    var cellToDelete by remember { mutableStateOf<String?>(null) }

    // 蓝牙弹窗状态
    var btBeingEdited by remember { mutableStateOf<EditableBluetoothItem?>(null) }
    var isNewBtDialog by remember { mutableStateOf(false) }
    var btToDelete by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timeStr =
        remember(item.location.timestamp) { dateFormat.format(Date(item.location.timestamp)) }

    val wifiList = remember(item, selectedWifiBssid) {
        val list = mutableListOf<EditableWifiItem>()
        val seenBssids = mutableSetOf<String>()

        item.connectedWifi?.let { cw ->
            seenBssids.add(cw.bssid.uppercase())
            list.add(
                EditableWifiItem(
                    bssid = cw.bssid,
                    ssid = cw.ssid,
                    frequency = cw.frequency,
                    level = cw.level,
                    capabilities = cw.capabilities,
                    vendor = cw.vendor,
                    isConnected = true,
                    isDesignated = selectedWifiBssid?.equals(cw.bssid, ignoreCase = true) == true
                )
            )
        }

        item.wifis.forEach { lw ->
            if (!seenBssids.contains(lw.device.bssid.uppercase())) {
                seenBssids.add(lw.device.bssid.uppercase())
                list.add(
                    EditableWifiItem(
                        bssid = lw.device.bssid,
                        ssid = lw.device.ssid,
                        frequency = lw.device.frequency,
                        level = lw.locationWifi.level,
                        capabilities = lw.device.capabilities,
                        vendor = lw.device.vendor,
                        isConnected = false,
                        isDesignated = selectedWifiBssid?.equals(lw.device.bssid, ignoreCase = true) == true
                    )
                )
            }
        }
        list
    }

    val cellList = remember(item, selectedCellKey) {
        val list = mutableListOf<EditableCellItem>()
        val seenKeys = mutableSetOf<String>()

        item.cells.forEach { lc ->
            if (!seenKeys.contains(lc.device.cellKey)) {
                seenKeys.add(lc.device.cellKey)
                list.add(
                    EditableCellItem(
                        cellKey = lc.device.cellKey,
                        type = lc.device.type,
                        mcc = lc.device.mcc,
                        mnc = lc.device.mnc,
                        tac = lc.device.tac,
                        ci = lc.device.ci,
                        pci = lc.device.pci,
                        lac = lc.device.lac,
                        cid = lc.device.cid,
                        psc = lc.device.psc,
                        nci = lc.device.nci,
                        networkId = lc.device.networkId,
                        systemId = lc.device.systemId,
                        basestationId = lc.device.basestationId,
                        dbm = lc.locationCell.dbm,
                        isRegistered = lc.locationCell.isRegistered,
                        isDesignated = selectedCellKey?.equals(lc.device.cellKey, ignoreCase = true) == true
                    )
                )
            }
        }
        list
    }

    val btList = remember(item, selectedBluetoothAddress) {
        val list = mutableListOf<EditableBluetoothItem>()
        val seenAddresses = mutableSetOf<String>()

        item.bluetooths.forEach { lb ->
            if (!seenAddresses.contains(lb.device.address.uppercase())) {
                seenAddresses.add(lb.device.address.uppercase())
                list.add(
                    EditableBluetoothItem(
                        address = lb.device.address,
                        name = lb.device.name,
                        scanRecordHex = lb.device.scanRecordHex,
                        rssi = lb.locationBluetooth.rssi,
                        isDesignated = selectedBluetoothAddress?.equals(lb.device.address, ignoreCase = true) == true
                    )
                )
            }
        }
        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // M3 全屏对话框：顶栏关闭 + 保存，内容区可滚动
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    title = {
                        Column {
                            Text(stringResource(R.string.edit_location_data), style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
            val parsedLat = latText.toDoubleOrNull() ?: item.location.lat
            val parsedLng = lngText.toDoubleOrNull() ?: item.location.lng
            onSave(parsedLat, parsedLng, placeName, remark, selectedWifiBssid, selectedBluetoothAddress, selectedCellKey)
                        }) {
                            Text(stringResource(R.string.save_changes))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .imePadding()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 坐标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.edit_coordinates_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ID: #${item.location.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text(stringResource(R.string.latitude)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text(stringResource(R.string.longitude)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(R.string.edit_coordinates_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalChip(Icons.Rounded.Wifi, "${wifiList.size} Wi-Fi", LabelTone.Primary)
                    SignalChip(Icons.Rounded.CellTower, "${cellList.size} ${stringResource(R.string.tag_cell)}", LabelTone.Warning)
                    SignalChip(Icons.Rounded.Bluetooth, "${btList.size} ${stringResource(R.string.tag_bluetooth)}", LabelTone.Tertiary)
                }

                // 地点名称与备注
                OutlinedTextField(
                    value = placeName,
                    onValueChange = { placeName = it },
                    label = { Text(stringResource(R.string.collection_location_name)) },
                    placeholder = { Text(stringResource(R.string.set_place_name_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    trailingIcon = if (placeName.isNotEmpty()) {
                        { IconButton(onClick = { placeName = "" }) { Icon(Icons.Rounded.Cancel, contentDescription = null) } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text(stringResource(R.string.remark_note)) },
                    placeholder = { Text(stringResource(R.string.add_remark_hint)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                    trailingIcon = if (remark.isNotEmpty()) {
                        { IconButton(onClick = { remark = "" }) { Icon(Icons.Rounded.Cancel, contentDescription = null) } }
                    } else null,
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // 选项卡：[Wi-Fi] [基站] [蓝牙]
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    EditDataTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = when (tab) {
                                        EditDataTab.WIFI -> stringResource(R.string.manage_tab_wifi, wifiList.size)
                                        EditDataTab.CELL -> stringResource(R.string.manage_tab_cell, cellList.size)
                                        EditDataTab.BLUETOOTH -> stringResource(R.string.manage_tab_bluetooth, btList.size)
                                    },
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                // 选项卡内容区
                when (selectedTab) {
                    EditDataTab.WIFI -> {
                        ManageWifiTabContent(
                            wifiList = wifiList,
                            selectedWifiBssid = selectedWifiBssid,
                            onSelectBssid = { selectedWifiBssid = it },
                            onAddWifi = {
                                isNewWifiDialog = true
                                wifiBeingEdited = null
                            },
                            onEditWifi = { wifi ->
                                wifiBeingEdited = wifi
                                isNewWifiDialog = false
                            },
                            onDeleteWifi = { bssid -> wifiToDelete = bssid }
                        )
                    }

                    EditDataTab.CELL -> {
                        ManageCellTabContent(
                            cellList = cellList,
                            selectedCellKey = selectedCellKey,
                            onSelectCellKey = { selectedCellKey = it },
                            onAddCell = {
                                isNewCellDialog = true
                                cellBeingEdited = null
                            },
                            onEditCell = { cell ->
                                cellBeingEdited = cell
                                isNewCellDialog = false
                            },
                            onDeleteCell = { cellKey -> cellToDelete = cellKey }
                        )
                    }

                    EditDataTab.BLUETOOTH -> {
                        ManageBluetoothTabContent(
                            btList = btList,
                            selectedBluetoothAddress = selectedBluetoothAddress,
                            onSelectAddress = { selectedBluetoothAddress = it },
                            onAddBluetooth = {
                                isNewBtDialog = true
                                btBeingEdited = null
                            },
                            onEditBluetooth = { bt ->
                                btBeingEdited = bt
                                isNewBtDialog = false
                            },
                            onDeleteBluetooth = { address -> btToDelete = address }
                        )
                    }
                }
            }
        }
    }

    // 编辑或新增 Wi-Fi 详细参数弹窗
    if (wifiBeingEdited != null || isNewWifiDialog) {
        EditWifiDialog(
            initialItem = wifiBeingEdited,
            onDismiss = {
                wifiBeingEdited = null
                isNewWifiDialog = false
            },
            onSave = { bssid, ssid, frequency, level, capabilities, vendor, isConnected, isDesignated ->
                onSaveWifi(bssid, ssid, frequency, level, capabilities, vendor, isConnected, isDesignated)
                if (isDesignated) {
                    selectedWifiBssid = bssid
                }
                wifiBeingEdited = null
                isNewWifiDialog = false
            }
        )
    }

    // 删除单项 Wi-Fi 二次确认
    if (wifiToDelete != null) {
        val targetBssid = wifiToDelete!!
        ManageDeleteConfirmDialog(
            message = stringResource(R.string.delete_wifi_confirm, targetBssid),
            onDismiss = { wifiToDelete = null },
            onConfirm = {
                onDeleteWifi(targetBssid)
                if (selectedWifiBssid?.equals(targetBssid, ignoreCase = true) == true) {
                    selectedWifiBssid = null
                }
                wifiToDelete = null
            }
        )
    }

    // 编辑或新增基站详细参数弹窗
    if (cellBeingEdited != null || isNewCellDialog) {
        EditCellDialog(
            initialItem = cellBeingEdited,
            onDismiss = {
                cellBeingEdited = null
                isNewCellDialog = false
            },
            onSave = { cellKey, type, mcc, mnc, tac, ci, pci, lac, cid, psc, nci, networkId, systemId, basestationId, dbm, isRegistered, isDesignated ->
                onSaveCell(cellKey, type, mcc, mnc, tac, ci, pci, lac, cid, psc, nci, networkId, systemId, basestationId, dbm, isRegistered, isDesignated)
                if (isDesignated) {
                    selectedCellKey = cellKey
                }
                cellBeingEdited = null
                isNewCellDialog = false
            }
        )
    }

    // 删除单项基站二次确认
    if (cellToDelete != null) {
        val targetCellKey = cellToDelete!!
        ManageDeleteConfirmDialog(
            message = stringResource(R.string.delete_cell_confirm, targetCellKey),
            onDismiss = { cellToDelete = null },
            onConfirm = {
                onDeleteCell(targetCellKey)
                if (selectedCellKey?.equals(targetCellKey, ignoreCase = true) == true) {
                    selectedCellKey = null
                }
                cellToDelete = null
            }
        )
    }

    // 编辑或新增蓝牙详细参数弹窗
    if (btBeingEdited != null || isNewBtDialog) {
        EditBluetoothDialog(
            initialItem = btBeingEdited,
            onDismiss = {
                btBeingEdited = null
                isNewBtDialog = false
            },
            onSave = { address, name, scanRecordHex, rssi, isDesignated ->
                onSaveBluetooth(address, name, scanRecordHex, rssi, isDesignated)
                if (isDesignated) {
                    selectedBluetoothAddress = address
                }
                btBeingEdited = null
                isNewBtDialog = false
            }
        )
    }

    // 删除单项蓝牙二次确认
    if (btToDelete != null) {
        val targetAddress = btToDelete!!
        ManageDeleteConfirmDialog(
            message = stringResource(R.string.delete_bt_confirm, targetAddress),
            onDismiss = { btToDelete = null },
            onConfirm = {
                onDeleteBluetooth(targetAddress)
                if (selectedBluetoothAddress?.equals(targetAddress, ignoreCase = true) == true) {
                    selectedBluetoothAddress = null
                }
                btToDelete = null
            }
        )
    }
}
