package com.vincenthzr.locationspoofer.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.RootSolution
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.ui.theme.noRippleClickable
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.confirmRestartHookedApps
import com.vincenthzr.locationspoofer.viewmodel.dismissRestartHookedAppsDialog
import com.vincenthzr.locationspoofer.viewmodel.dismissRootSetupTestResult
import com.vincenthzr.locationspoofer.viewmodel.requestRestartHookedApps
import com.vincenthzr.locationspoofer.viewmodel.setRootSolution
import com.vincenthzr.locationspoofer.viewmodel.testRootSetup
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.components.AppTopBar
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RootDiagnosticsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AppTopBar(
                title = stringResource(R.string.root_solution_title),
                subtitle = stringResource(R.string.root_solution_desc),
                onBack = onClose
            )

            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(AccentBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Shield,
                                    null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.root_solution_title),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.root_solution_desc),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val rootSolutions = listOf(
                            RootSolution.AUTO to stringResource(R.string.root_solution_auto),
                            RootSolution.MAGISK to stringResource(R.string.root_solution_magisk),
                            RootSolution.KERNELSU to stringResource(R.string.root_solution_kernelsu),
                            RootSolution.APATCH to stringResource(R.string.root_solution_apatch),
                            RootSolution.SUKISU_ULTRA to stringResource(R.string.root_solution_sukisu_ultra),
                            RootSolution.RESUKISU_ULTRA to stringResource(R.string.root_solution_resukisu_ultra)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rootSolutions.forEach { (solution, label) ->
                                FilterChip(
                                    selected = uiState.rootSolution == solution,
                                    onClick = { viewModel.setRootSolution(solution) },
                                    label = { Text(text = label) }
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 测试 Root 权限与框架配置通道
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .noRippleClickable {
                                    if (!uiState.isTestingRootSetup) viewModel.testRootSetup()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.test_root_setup_title),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.test_root_setup_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }

                            if (uiState.isTestingRootSetup) {
                                CircularProgressIndicator(
                                    color = AccentBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = stringResource(R.string.test_root_setup_title),
                                    tint = AccentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 重启目标应用，让它们加载当前模块并重新订阅框架配置。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .noRippleClickable {
                                    if (!uiState.isRestartingHookedApps) viewModel.requestRestartHookedApps()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.restart_hooked_apps_title),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.restart_hooked_apps_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }

                            if (uiState.isRestartingHookedApps) {
                                CircularProgressIndicator(
                                    color = AccentOrange,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = stringResource(R.string.restart_hooked_apps_title),
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.40f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }

    val testResult = uiState.rootSetupTestResult
    if (testResult != null) {
        RootSetupTestResultDialog(
            result = testResult,
            onDismiss = { viewModel.dismissRootSetupTestResult() }
        )
    }

    val appsToRestart = uiState.hookedAppsToRestart
    if (appsToRestart != null) {
        RestartHookedAppsConfirmDialog(
            apps = appsToRestart,
            onDismiss = { viewModel.dismissRestartHookedAppsDialog() },
            onConfirm = {
                viewModel.confirmRestartHookedApps { count ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.restart_hooked_apps_done, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}
