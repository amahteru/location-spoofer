package io.github.amahteru.locationspoofer.ui.screen.managedata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.components.LabelTone
import io.github.amahteru.locationspoofer.ui.components.StatusLabel
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

data class DeviceBadge(val text: String, val tone: LabelTone)

@Composable
fun <T> DesignatedDeviceList(
    title: String,
    addLabel: String,
    tip: String,
    autoLabel: String,
    emptyText: String,
    items: List<T>,
    isSelected: (T) -> Boolean,
    headline: (T) -> String,
    badges: @Composable (T) -> List<DeviceBadge>,
    details: (T) -> List<String>,
    onSelectAuto: () -> Unit,
    onSelect: (T) -> Unit,
    onAdd: () -> Unit,
    onEdit: (T) -> Unit,
    onDelete: (T) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.lg, end = AppSpacing.sm, top = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAdd, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(addLabel)
            }
        }
        Text(
            text = tip,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.lg)
        )

        Column(modifier = Modifier.selectableGroup().padding(vertical = AppSpacing.sm)) {
            val autoSelected = items.none(isSelected)
            DeviceRow(
                selected = autoSelected,
                onSelect = onSelectAuto,
                headline = { Text(autoLabel) }
            )

            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.lg)
                )
            }

            items.forEach { item ->
                val itemBadges = badges(item)
                DeviceRow(
                    selected = isSelected(item),
                    onSelect = { onSelect(item) },
                    headline = {
                        Text(headline(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supporting = {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            details(item).forEach { line -> Text(line, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            if (itemBadges.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                    itemBadges.forEach { StatusLabel(text = it.text, tone = it.tone) }
                                }
                            }
                        }
                    },
                    trailing = {
                        Row {
                            IconButton(onClick = { onEdit(item) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                            }
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    selected: Boolean,
    onSelect: () -> Unit,
    headline: @Composable () -> Unit,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = headline,
        supportingContent = supporting,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
