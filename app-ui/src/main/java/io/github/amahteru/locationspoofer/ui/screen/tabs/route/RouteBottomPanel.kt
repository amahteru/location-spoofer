package io.github.amahteru.locationspoofer.ui.screen.tabs.route

import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    searchBar: (@Composable (Modifier) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = AppMotion.defaultSpatialSpec()
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            if (searchBar != null) {
                searchBar(Modifier.fillMaxWidth())
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 3.dp
            ) {
                Box(modifier = Modifier.padding(AppSpacing.lg)) {
                    when (stage) {
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = AppSpacing.sm)
                                ) {
                                    Icon(
                                        Icons.Rounded.Navigation,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(AppSpacing.sm))
                                    Text(
                                        text = if (uiState.routeRunMode == RouteRunMode.MANUAL) {
                                        stringResource(R.string.joystick_controlling_active)
                                    } else {
                                        val speedMs = if (uiState.routeSimMode == SimMode.CUSTOM) uiState.customSpeedMs else uiState.routeSimMode.speedMs
                                        stringResource(R.string.cruising_speed_format, String.format(Locale.US, "%.1f", speedMs))
                                    },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = onStopRoute,
                                    modifier = Modifier.height(AppSpacing.actionButtonHeight),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                                ) {
                                    Icon(
                                        Icons.Rounded.StopCircle,
                                        null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(
                                        stringResource(R.string.stop_simulation)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
