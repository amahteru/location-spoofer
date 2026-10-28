package io.github.amahteru.locationspoofer.ui.screen.managedata

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.components.LabelTone

@Composable
fun ManageWifiTabContent(
    wifiList: List<EditableWifiItem>,
    selectedWifiBssid: String?,
    onSelectBssid: (String?) -> Unit,
    onAddWifi: () -> Unit,
    onEditWifi: (EditableWifiItem) -> Unit,
    onDeleteWifi: (String) -> Unit
) {
    val designatedLabel = stringResource(R.string.designated_simulation_badge)
    val connectedLabel = stringResource(R.string.connected_wifi_badge)
    DesignatedDeviceList(
        title = stringResource(R.string.manage_wifi_list_title),
        addLabel = stringResource(R.string.add_wifi_btn),
        tip = stringResource(R.string.designated_wifi_tip),
        autoLabel = stringResource(R.string.no_designated_wifi),
        emptyText = stringResource(R.string.no_wifi_data_in_record),
        items = wifiList,
        isSelected = { selectedWifiBssid?.equals(it.bssid, ignoreCase = true) == true },
        headline = { it.ssid.ifBlank { "<Hidden SSID>" } },
        badges = { wifi ->
            buildList {
                if (selectedWifiBssid?.equals(wifi.bssid, ignoreCase = true) == true) add(DeviceBadge(designatedLabel, LabelTone.Primary))
                if (wifi.isConnected) add(DeviceBadge(connectedLabel, LabelTone.Success))
            }
        },
        details = { wifi ->
            listOfNotNull(
                "${wifi.bssid}  •  ${wifi.level} dBm  •  ${wifi.frequency} MHz",
                wifi.capabilities.takeIf { it.isNotBlank() }
            )
        },
        onSelectAuto = { onSelectBssid(null) },
        onSelect = { onSelectBssid(it.bssid) },
        onAdd = onAddWifi,
        onEdit = onEditWifi,
        onDelete = { onDeleteWifi(it.bssid) }
    )
}
