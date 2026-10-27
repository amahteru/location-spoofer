package com.vincenthzr.locationspoofer.ui.screen

import androidx.core.net.toUri
import androidx.compose.material.icons.rounded.PauseCircle
import com.vincenthzr.locationspoofer.ui.components.onContainerOf
import com.vincenthzr.locationspoofer.ui.components.containerOf
import com.vincenthzr.locationspoofer.ui.components.ToneIcon
import com.vincenthzr.locationspoofer.ui.components.LabelTone
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.ImportExportSelection
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.exportEnvironmentData
import com.vincenthzr.locationspoofer.viewmodel.toggleContinuousScanning
import com.vincenthzr.locationspoofer.ui.components.AppCard
import com.vincenthzr.locationspoofer.ui.theme.AppSpacing

private val REQUIRED_PERMISSIONS: List<String>
    get() = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

@Composable
fun DataCollectionAssistScreen(
    viewModel: MainViewModel,
    uiState: AppState) {
    val context = LocalContext.current
    val resources = LocalResources.current

    var missingPermissions by remember {
        mutableStateOf(REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportEnvironmentData(
                it,
                ImportExportSelection(
                    locations = true,
                    savedLocations = false,
                    savedRoutes = false,
                    settings = false,
                    apiKeys = false
                )
            ) { success ->
                Toast.makeText(
                    context,
                    resources.getString(if (success) R.string.export_success else R.string.export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(AppSpacing.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIcon(Icons.Rounded.Lock, LabelTone.Error)
                    Spacer(Modifier.width(AppSpacing.lg))
                    Column {
                        Text(
                            text = stringResource(R.string.root_required),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.root_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    text = stringResource(R.string.data_collection_assist_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (missingPermissions.isNotEmpty()) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(AppSpacing.lg)
                ) {
                    Text(
                        text = stringResource(R.string.data_collection_permission_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    Button(
                        onClick = {
                            val activity = context as? androidx.activity.ComponentActivity
                            val stillHidden = missingPermissions.any {
                                activity != null && !activity.shouldShowRequestPermissionRationale(it)
                            }
                            if (stillHidden) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            "package:${context.packageName}".toUri()
                                        )
                                    )
                                } catch (_: Exception) {
                                }
                            } else {
                                permissionLauncher.launch(missingPermissions.toTypedArray())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.go_to_settings))
                    }
                }
            }

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(AppSpacing.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (uiState.isContinuousScanning) Icons.Rounded.Radar else Icons.Rounded.PauseCircle,
                        contentDescription = null,
                        tint = if (uiState.isContinuousScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(
                        text = if (uiState.isContinuousScanning) {
                            stringResource(R.string.scanning_status_active, uiState.environmentRecordCount)
                        } else {
                            stringResource(R.string.scanning_status_inactive)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(AppSpacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    ScanCountBadge(
                        icon = Icons.Rounded.Wifi,
                        tone = LabelTone.Primary,
                        text = stringResource(R.string.scanned_wifi_count, uiState.scannedWifiCount),
                        modifier = Modifier.weight(1f)
                    )
                    ScanCountBadge(
                        icon = Icons.Rounded.CellTower,
                        tone = LabelTone.Warning,
                        text = stringResource(R.string.scanned_cell_count, uiState.scannedCellCount),
                        modifier = Modifier.weight(1f)
                    )
                    ScanCountBadge(
                        icon = Icons.Rounded.Bluetooth,
                        tone = LabelTone.Tertiary,
                        text = stringResource(R.string.scanned_bt_count, uiState.scannedBluetoothCount),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(AppSpacing.lg))

                Text(
                    text = stringResource(R.string.data_collection_assist_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(AppSpacing.lg))

                Button(
                    onClick = { viewModel.toggleContinuousScanning() },
                    enabled = !uiState.isStoppingCollection,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    colors = if (uiState.isContinuousScanning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = if (uiState.isStoppingCollection) {
                            stringResource(R.string.collection_stopping)
                        } else if (uiState.isContinuousScanning) {
                            stringResource(R.string.stop_collection)
                        } else {
                            stringResource(R.string.start_collection)
                        }
                    )
                }
            }

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(AppSpacing.lg)
            ) {
                if (uiState.environmentRecordCount > 0) {
                    Button(
                        onClick = {
                            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                .format(java.util.Date())
                            exportLauncher.launch("environment_data_$stamp.json")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(stringResource(R.string.export_data))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.no_data_collected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanCountBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: LabelTone,
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = colors.containerOf(tone),
        contentColor = colors.onContainerOf(tone)
    ) {
        Column(
            modifier = Modifier.padding(vertical = AppSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(AppSpacing.xs))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
