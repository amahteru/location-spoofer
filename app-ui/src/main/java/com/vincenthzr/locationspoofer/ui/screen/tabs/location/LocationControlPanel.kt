package com.vincenthzr.locationspoofer.ui.screen.tabs.location

import com.vincenthzr.locationspoofer.ui.theme.AppMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.SavedLocation
import com.vincenthzr.locationspoofer.ui.screen.ActionButtons
import com.vincenthzr.locationspoofer.ui.screen.CoordinateInputCard
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun LocationControlPanel(
    modifier: Modifier,
    uiState: AppState,
    panelState: LocationPanelState,
    onPanelStateChange: (LocationPanelState) -> Unit,
    viewModel: MainViewModel,
    tabBarHeight: Dp,
    savedCardHeightDp: Dp,
    coordCardHeightDp: Dp,
    onSavedCardHeightMeasured: (Int) -> Unit,
    onCoordCardHeightMeasured: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onCustomClick: () -> Unit,
    onStartFixedSpoofing: () -> Unit,
    onStopSpoofing: () -> Unit,
    onSelectSavedLocation: (SavedLocation) -> Unit,
    onDeleteSavedLocation: (SavedLocation) -> Unit,
    onOpenManageSavedLocations: () -> Unit,
    searchBar: @Composable (Modifier) -> Unit
) {
    val motion = AppMotion
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val dragGestureModifier = Modifier.pointerInput(panelState) {
        detectVerticalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onVerticalDrag = { _, dragAmount ->
                dragAccumulator += dragAmount
            },
            onDragEnd = {
                if (dragAccumulator > 28f) {
                    when (panelState) {
                        LocationPanelState.EXPANDED -> onPanelStateChange(LocationPanelState.DEFAULT)
                        LocationPanelState.DEFAULT -> onPanelStateChange(LocationPanelState.COLLAPSED)
                        LocationPanelState.COLLAPSED -> {}
                    }
                } else if (dragAccumulator < -28f) {
                    when (panelState) {
                        LocationPanelState.COLLAPSED -> onPanelStateChange(LocationPanelState.DEFAULT)
                        LocationPanelState.DEFAULT -> onPanelStateChange(LocationPanelState.EXPANDED)
                        LocationPanelState.EXPANDED -> {}
                    }
                }
                dragAccumulator = 0f
            },
            onDragCancel = { dragAccumulator = 0f }
        )
    }

    val rawDrawerOffsetY by animateDpAsState(
        targetValue = when (panelState) {
            LocationPanelState.EXPANDED -> 0.dp
            LocationPanelState.DEFAULT -> savedCardHeightDp + AppSpacing.sm
            LocationPanelState.COLLAPSED -> savedCardHeightDp + coordCardHeightDp + AppSpacing.sm * 2
        },
        animationSpec = motion.defaultSpatialSpec(),
        label = "drawer_offset_y"
    )

    val drawerOffsetYPx = with(density) { rawDrawerOffsetY.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg)
            .padding(bottom = (tabBarHeight + AppSpacing.sm).coerceAtLeast(0.dp))
            .offset { IntOffset(0, drawerOffsetYPx.roundToInt()) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.xs, bottom = AppSpacing.xs)
                    .then(dragGestureModifier)
                    .clickable {
                        val next = when (panelState) {
                            LocationPanelState.COLLAPSED -> LocationPanelState.DEFAULT
                            LocationPanelState.DEFAULT -> LocationPanelState.EXPANDED
                            LocationPanelState.EXPANDED -> LocationPanelState.DEFAULT
                        }
                        onPanelStateChange(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.xs)
                    .then(dragGestureModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                searchBar(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                )

                AnimatedVisibility(
                    visible = uiState.isSpoofingActive,
                    enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
                ) {
                    ExtendedFloatingActionButton(
                        onClick = onStopSpoofing,
                        icon = { Icon(Icons.Rounded.StopCircle, contentDescription = null) },
                        text = { Text(stringResource(R.string.stop_simulation)) },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(start = AppSpacing.sm)
                    )
                }

                AnimatedVisibility(
                    visible = !uiState.isSpoofingActive && panelState == LocationPanelState.COLLAPSED,
                    enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { if (!uiState.isSavingConfig) onStartFixedSpoofing() },
                        icon = {
                            if (uiState.isSavingConfig) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = LocalContentColor.current,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.MyLocation, contentDescription = null)
                            }
                        },
                        text = {
                            Text(stringResource(if (uiState.isSavingConfig) R.string.starting_ellipsis else R.string.fixed_simulation))
                        },
                        modifier = Modifier.padding(start = AppSpacing.sm)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        if (it.size.height > 0) {
                            onCoordCardHeightMeasured(it.size.height)
                        }
                    }
                    .then(dragGestureModifier),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.lg)
                ) {
                    CoordinateInputCard(
                        viewModel = viewModel,
                        uiState = uiState,
                        onSaveClick = onSaveClick,
                        onCustomClick = onCustomClick
                    )

                    Spacer(Modifier.height(AppSpacing.md))

                    ActionButtons(
                        viewModel = viewModel,
                        uiState = uiState,
                        onOpenMap = {},
                        onStartFixedSpoofing = onStartFixedSpoofing
                    )
                }
            }

            SavedLocationsCard(
                modifier = Modifier.onGloballyPositioned {
                    if (it.size.height > 0) {
                        onSavedCardHeightMeasured(it.size.height)
                    }
                },
                savedLocations = uiState.savedLocations,
                panelState = panelState,
                onSelect = onSelectSavedLocation,
                onDelete = onDeleteSavedLocation,
                onToggleExpand = {
                    val next = if (panelState == LocationPanelState.EXPANDED) {
                        LocationPanelState.DEFAULT
                    } else {
                        LocationPanelState.EXPANDED
                    }
                    onPanelStateChange(next)
                },
                onOpenManageDialog = onOpenManageSavedLocations,
                dragGestureModifier = dragGestureModifier
            )
        }
    }
}
