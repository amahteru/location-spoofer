package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.clearPinnedCollectedLocation


// 坐标输入区域：直接放在定位抽屉的浮层里，不再自带卡片底色
@Composable
fun CoordinateInputCard(
    viewModel: MainViewModel,
    uiState: AppState,
    onSaveClick: () -> Unit,
    onCustomClick: () -> Unit
) {
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                Icons.Outlined.PinDrop,
                stringResource(R.string.target_coordinates))
            Spacer(Modifier.weight(1f))
            // TextButton 自带右侧 16dp 内边距；整组右移 8dp，让按钮文字距卡片右缘 24dp，避开右上角大圆角处的拥挤感
            Row(modifier = Modifier.offset(x = 8.dp)) {
                TextButton(
                    onClick = onCustomClick,
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.custom))
                }
                TextButton(
                    onClick = onSaveClick,
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                ) {
                    Icon(Icons.Rounded.StarBorder, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.save))
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Longitude Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                    Text(
                        text = uiState.longitudeInput.ifEmpty { "0.0" },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                }
            }

            // Latitude Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.latitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                    Text(
                        text = uiState.latitudeInput.ifEmpty { "0.0" },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                }
            }
        }

        if (uiState.pinnedCollectedLocationId != null) {
            Spacer(Modifier.height(10.dp))
            InputChip(
                selected = true,
                onClick = { viewModel.clearPinnedCollectedLocation() },
                label = {
                    Text(
                        text = stringResource(
                            R.string.pinned_collected_location_badge,
                            uiState.pinnedLocationName ?: ""
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.PinDrop, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize))
                },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.unpin_collected_location),
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                }
            )
        }

        if (uiState.showCoordinateError) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.invalid_coordinates),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
