package io.github.amahteru.locationspoofer.ui.screen

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
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.clearPinnedCollectedLocation
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

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
                stringResource(R.string.target_coordinates),
                modifier = Modifier.weight(1f))
            Row(modifier = Modifier.offset(x = 8.dp)) {
                TextButton(
                    onClick = onCustomClick,
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.custom_coordinate_btn))
                }
                TextButton(
                    onClick = onSaveClick,
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                ) {
                    Icon(Icons.Rounded.StarBorder, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.bookmark))
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.md))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
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
            Spacer(Modifier.height(AppSpacing.md))
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
            Spacer(Modifier.height(AppSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(AppSpacing.xs))
                Text(
                    stringResource(R.string.invalid_coordinates),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
