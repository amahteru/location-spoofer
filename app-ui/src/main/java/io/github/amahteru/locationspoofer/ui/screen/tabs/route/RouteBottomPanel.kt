package io.github.amahteru.locationspoofer.ui.screen.tabs.route

import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.data.model.RoutePlanStage
import io.github.amahteru.locationspoofer.data.model.RoutePoint
import io.github.amahteru.locationspoofer.data.model.RouteRunMode
import io.github.amahteru.locationspoofer.data.model.SimMode
import java.util.Locale
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun RouteBottomPanel(
    modifier: Modifier,
    stage: RoutePlanStage,
    routePoints: List<RoutePoint>,
    uiState: AppState,
    onConfirmPoint: () -> Unit,
    onFinishSelecting: () -> Unit,
    onRestartSelecting: () -> Unit,
    onSaveRoute: () -> Unit,
    onStartPlanning: () -> Unit,
    onStopRoute: () -> Unit,
    onPauseRoute: () -> Unit,
    onResumeRoute: () -> Unit,
    onHoldPosition: () -> Unit,
    onReplanRoute: () -> Unit,
    searchBar: @Composable (Modifier) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = stage == RoutePlanStage.IDLE || stage == RoutePlanStage.SELECTING,
            enter = fadeIn(AppMotion.defaultEffectsSpec()) + expandVertically(AppMotion.defaultSpatialSpec()),
            exit = fadeOut(AppMotion.fastEffectsSpec()) + shrinkVertically(AppMotion.fastSpatialSpec())
        ) {
            searchBar(Modifier.fillMaxWidth().padding(bottom = AppSpacing.md))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 3.dp
        ) {
            AnimatedContent(
                targetState = stage,
                modifier = Modifier.padding(AppSpacing.lg),
                contentKey = { if (it == RoutePlanStage.IDLE) RoutePlanStage.SELECTING else it },
                contentAlignment = Alignment.BottomCenter,
                transitionSpec = {
                    fadeIn(AppMotion.defaultEffectsSpec()) togetherWith fadeOut(AppMotion.fastEffectsSpec()) using
                        SizeTransform { _, _ -> AppMotion.defaultSpatialSpec() }
                },
                label = "route_panel_stage"
            ) { panelStage ->
                when (panelStage) {
                    RoutePlanStage.IDLE, RoutePlanStage.SELECTING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onConfirmPoint,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(AppSpacing.actionButtonHeight)
                            ) {
                                Icon(
                                    Icons.Rounded.AddLocation,
                                    null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(
                                    if (routePoints.isEmpty()) stringResource(R.string.add_start_point) else stringResource(R.string.add_nth_point, routePoints.size + 1)
                                )
                            }

                            FilledTonalButton(
                                onClick = onFinishSelecting,
                                enabled = routePoints.size >= 2,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(AppSpacing.actionButtonHeight)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(
                                    stringResource(R.string.finish_selecting)
                                )
                            }
                        }
                    }

                    RoutePlanStage.READY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onRestartSelecting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(AppSpacing.actionButtonHeight),
                                contentPadding = PaddingValues(horizontal = AppSpacing.xs)
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(
                                    stringResource(R.string.reselect),
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = onSaveRoute,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(AppSpacing.actionButtonHeight),
                                contentPadding = PaddingValues(horizontal = AppSpacing.xs)
                            ) {
                                Icon(
                                    Icons.Rounded.StarBorder,
                                    null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(
                                    stringResource(R.string.bookmark),
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = onStartPlanning,
                                modifier = Modifier
                                    .weight(1.35f)
                                    .height(AppSpacing.actionButtonHeight),
                                contentPadding = PaddingValues(horizontal = AppSpacing.sm)
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(
                                    stringResource(R.string.start_simulation),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    RoutePlanStage.RUNNING -> {
                        RouteRunningControls(
                            uiState = uiState,
                            onPauseRoute = onPauseRoute,
                            onResumeRoute = onResumeRoute,
                            onHoldPosition = onHoldPosition,
                            onReplanRoute = onReplanRoute,
                            onStopRoute = onStopRoute
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteRunningControls(
    uiState: AppState,
    onPauseRoute: () -> Unit,
    onResumeRoute: () -> Unit,
    onHoldPosition: () -> Unit,
    onReplanRoute: () -> Unit,
    onStopRoute: () -> Unit
) {
    var showEndMenu by remember { mutableStateOf(false) }
    val paused = uiState.isRoutePaused
    val isManual = uiState.routeRunMode == RouteRunMode.MANUAL
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                if (paused && !isManual) Icons.Rounded.PersonPinCircle else Icons.Rounded.Navigation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(AppSpacing.lg))
            Column {
                Text(
                    text = stringResource(
                        when {
                            isManual -> R.string.joystick_controlling_active
                            paused -> R.string.route_paused
                            else -> R.string.route_cruising
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isManual) {
                    val speedMs = if (uiState.routeSimMode == SimMode.CUSTOM) uiState.customSpeedMs else uiState.routeSimMode.speedMs
                    Text(
                        text = stringResource(R.string.route_speed_format, String.format(Locale.US, "%.1f", speedMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = if (paused) onResumeRoute else onPauseRoute,
            modifier = Modifier.size(AppSpacing.actionButtonHeight)
        ) {
            Icon(
                if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = stringResource(if (paused) R.string.route_resume else R.string.route_pause)
            )
        }

        Box {
            OutlinedButton(
                onClick = { showEndMenu = true },
                modifier = Modifier.height(AppSpacing.actionButtonHeight)
            ) {
                Text(stringResource(R.string.route_end))
            }
            DropdownMenu(
                expanded = showEndMenu,
                onDismissRequest = { showEndMenu = false },
                shape = MaterialTheme.shapes.large
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.route_hold_here)) },
                    leadingIcon = { Icon(Icons.Rounded.PinDrop, null) },
                    onClick = {
                        showEndMenu = false
                        onHoldPosition()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.route_replan_from_here)) },
                    leadingIcon = { Icon(Icons.Rounded.AltRoute, null) },
                    onClick = {
                        showEndMenu = false
                        onReplanRoute()
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.xs))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.route_stop_restore_real)) },
                    leadingIcon = { Icon(Icons.Rounded.StopCircle, null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        showEndMenu = false
                        onStopRoute()
                    }
                )
            }
        }
    }
}
