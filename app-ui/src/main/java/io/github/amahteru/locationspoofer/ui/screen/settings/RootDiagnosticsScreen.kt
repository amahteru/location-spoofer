package io.github.amahteru.locationspoofer.ui.screen.settings

import io.github.amahteru.locationspoofer.ui.components.NavigationListItem
import io.github.amahteru.locationspoofer.ui.components.LabelTone
import androidx.compose.material3.Card
import io.github.amahteru.locationspoofer.ui.components.SubPageScaffold
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
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.data.model.RootSolution
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.confirmRestartHookedApps
import io.github.amahteru.locationspoofer.viewmodel.dismissRestartHookedAppsDialog
import io.github.amahteru.locationspoofer.viewmodel.dismissRootSetupTestResult
import io.github.amahteru.locationspoofer.viewmodel.requestRestartHookedApps
import io.github.amahteru.locationspoofer.viewmodel.setRootSolution
import io.github.amahteru.locationspoofer.viewmodel.testRootSetup
import io.github.amahteru.locationspoofer.ui.components.AppCard
import io.github.amahteru.locationspoofer.ui.components.AppTopBar
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

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

@Composable
private fun ActionProgressSlot(running: Boolean) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (running) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
