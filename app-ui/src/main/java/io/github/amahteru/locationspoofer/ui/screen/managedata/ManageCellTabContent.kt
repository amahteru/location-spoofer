package io.github.amahteru.locationspoofer.ui.screen.managedata

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.components.LabelTone

@Composable
fun ManageCellTabContent(
    cellList: List<EditableCellItem>,
    selectedCellKey: String?,
    onSelectCellKey: (String?) -> Unit,
    onAddCell: () -> Unit,
    onEditCell: (EditableCellItem) -> Unit,
    onDeleteCell: (String) -> Unit
) {
    val designatedLabel = stringResource(R.string.designated_cell_badge)
    val registeredLabel = stringResource(R.string.registered_cell_badge)
    DesignatedDeviceList(
        title = stringResource(R.string.manage_cell_list_title),
        addLabel = stringResource(R.string.add_cell_btn),
        tip = stringResource(R.string.designated_cell_tip),
        autoLabel = stringResource(R.string.no_designated_cell),
        emptyText = stringResource(R.string.no_cell_data_in_record),
        items = cellList,
        isSelected = { selectedCellKey?.equals(it.cellKey, ignoreCase = true) == true },
        headline = { "${it.type.uppercase()} • MCC:${it.mcc} MNC:${it.mnc}" },
        badges = { cell ->
            buildList {
                if (selectedCellKey?.equals(cell.cellKey, ignoreCase = true) == true) add(DeviceBadge(designatedLabel, LabelTone.Primary))
                if (cell.isRegistered) add(DeviceBadge(registeredLabel, LabelTone.Warning))
            }
        },
        details = { cell ->
            listOf(
                when (cell.type.uppercase()) {
                    "LTE" -> "TAC: ${cell.tac}  •  CI: ${cell.ci}  •  PCI: ${cell.pci}  •  ${cell.dbm} dBm"
                    "NR" -> "TAC: ${cell.tac}  •  NCI: ${cell.nci}  •  PCI: ${cell.pci}  •  ${cell.dbm} dBm"
                    "GSM" -> "LAC: ${cell.lac}  •  CID: ${cell.cid}  •  ${cell.dbm} dBm"
                    "WCDMA" -> "LAC: ${cell.lac}  •  CID: ${cell.cid}  •  PSC: ${cell.psc}  •  ${cell.dbm} dBm"
                    else -> "Key: ${cell.cellKey}  •  ${cell.dbm} dBm"
                }
            )
        },
        onSelectAuto = { onSelectCellKey(null) },
        onSelect = { onSelectCellKey(it.cellKey) },
        onAdd = onAddCell,
        onEdit = onEditCell,
        onDelete = { onDeleteCell(it.cellKey) }
    )
}
