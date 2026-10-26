package com.vincenthzr.locationspoofer.ui.screen.settings

import com.vincenthzr.locationspoofer.ui.components.NavigationListItem
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import androidx.compose.material3.Card
import com.vincenthzr.locationspoofer.ui.components.SubPageScaffold
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.RootSolution
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
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RootDiagnosticsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.root_solution_title),
        subtitle = stringResource(R.string.root_solution_desc),
        onBack = onClose
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            val rootSolutions = listOf(
                RootSolution.AUTO to stringResource(R.string.root_solution_auto),
                RootSolution.MAGISK to stringResource(R.string.root_solution_magisk),
                RootSolution.KERNELSU to stringResource(R.string.root_solution_kernelsu),
                RootSolution.APATCH to stringResource(R.string.root_solution_apatch),
                RootSolution.SUKISU_ULTRA to stringResource(R.string.root_solution_sukisu_ultra),
                RootSolution.RESUKISU_ULTRA to stringResource(R.string.root_solution_resukisu_ultra)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                rootSolutions.forEach { (solution, label) ->
                    FilterChip(
                        selected = uiState.rootSolution == solution,
                        onClick = { viewModel.setRootSolution(solution) },
                        label = { Text(text = label) }
                    )
                }
            }
        }

        // 测试 Root 权限与框架配置通道；重启目标应用，让它们加载当前模块并重新订阅框架配置。
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = AppSpacing.sm)) {
                NavigationListItem(
                    icon = Icons.Rounded.VerifiedUser,
                    title = stringResource(R.string.test_root_setup_title),
                    summary = stringResource(R.string.test_root_setup_desc),
                    tone = LabelTone.Primary,
                    onClick = { if (!uiState.isTestingRootSetup) viewModel.testRootSetup() },
                    trailing = { ActionProgressSlot(uiState.isTestingRootSetup) }
                )
                NavigationListItem(
                    icon = Icons.Rounded.RestartAlt,
                    title = stringResource(R.string.restart_hooked_apps_title),
                    summary = stringResource(R.string.restart_hooked_apps_desc),
                    tone = LabelTone.Warning,
                    onClick = { if (!uiState.isRestartingHookedApps) viewModel.requestRestartHookedApps() },
                    trailing = { ActionProgressSlot(uiState.isRestartingHookedApps) }
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
                        resources.getString(R.string.restart_hooked_apps_done, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

/** 列表项尾部固定 24dp 的进度位：空闲时留空、执行中显示转圈，宽度不变，文字不会因此重新折行 */
@Composable
private fun ActionProgressSlot(running: Boolean) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (running) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
