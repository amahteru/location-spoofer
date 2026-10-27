package com.vincenthzr.locationspoofer.ui.screen.tabs

import com.vincenthzr.locationspoofer.ui.components.AppRootTopBar
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.screen.ImportExportDataCard
import com.vincenthzr.locationspoofer.ui.screen.ManageDataCard
import com.vincenthzr.locationspoofer.ui.screen.ScannerMapCard
import com.vincenthzr.locationspoofer.ui.components.ImportExportSelectionDialog
import com.vincenthzr.locationspoofer.data.model.ImportExportCounts
import com.vincenthzr.locationspoofer.data.model.ImportExportSelection
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.applyImportPackage
import com.vincenthzr.locationspoofer.viewmodel.collectExportCounts
import com.vincenthzr.locationspoofer.viewmodel.exportEnvironmentData
import com.vincenthzr.locationspoofer.viewmodel.parseImportPackage
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

@Composable
fun FeaturesTab(
    viewModel: MainViewModel,
    uiState: AppState,
    tabBarHeight: Dp = 90.dp,
    onNavigateToScanner: () -> Unit,
    onNavigateToManageData: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    var exportCounts by remember { mutableStateOf<ImportExportCounts?>(null) }
    var exportSelection by remember { mutableStateOf(ImportExportSelection()) }
    var pendingImportPackage by remember {
        mutableStateOf<com.vincenthzr.locationspoofer.data.db.LocationSpooferDataPackage?>(null)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportEnvironmentData(it, exportSelection) { success ->
                Toast.makeText(
                    context,
                    resources.getString(if (success) R.string.export_success else R.string.export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.parseImportPackage(it) { pkg ->
                if (pkg == null) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.import_parse_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    pendingImportPackage = pkg
                }
            }
        }
    }

    exportCounts?.let { counts ->
        ImportExportSelectionDialog(
            isExport = true,
            counts = counts,
            onConfirm = { selection ->
                exportSelection = selection
                exportCounts = null
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                exportLauncher.launch("environment_data_$stamp.json")
            },
            onDismiss = { exportCounts = null }
        )
    }

    pendingImportPackage?.let { pkg ->
        ImportExportSelectionDialog(
            isExport = false,
            counts = ImportExportCounts(
                locations = pkg.locations.size + pkg.collectionRoutes.size,
                savedLocations = pkg.savedLocations.size,
                savedRoutes = pkg.savedRoutes.size,
                settings = if (pkg.settings != null) 1 else 0,
                apiKeys = if (pkg.apiKeys != null) 1 else 0
            ),
            onConfirm = { selection ->
                pendingImportPackage = null
                viewModel.applyImportPackage(pkg, selection) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.import_merge_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { pendingImportPackage = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppRootTopBar(
                title = stringResource(R.string.features_tab_title),
                subtitle = stringResource(R.string.features_tab_desc)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.lg),
                contentPadding = PaddingValues(bottom = tabBarHeight + AppSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                item {
                    ScannerMapCard(
                        uiState = uiState,
                        onClick = onNavigateToScanner
                    )
                }

                item {
                    ManageDataCard(
                        onClick = onNavigateToManageData
                    )
                }

                item {
                    ImportExportDataCard(
                        onImportClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onExportClick = {
                            viewModel.collectExportCounts { counts -> exportCounts = counts }
                        }
                    )
                }
            }
        }
    }
}
