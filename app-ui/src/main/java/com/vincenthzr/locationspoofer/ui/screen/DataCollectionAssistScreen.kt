package com.vincenthzr.locationspoofer.ui.screen

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

private val REQUIRED_PERMISSIONS: List<String>
    get() = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

/**
 * 没有 root 时的落地页：主功能（虚拟定位注入）用不了，但环境数据采集
 * （EnvironmentScanner.scanWifi/scanCell/scanBluetooth）纯走标准 Android API，
 * 不依赖 root/LSPosed，所以让没有 root 的设备也能完成"实地采集 + 导出"，
 * 交给另一台有 root 的设备导入使用。
 */
@Composable
fun DataCollectionAssistScreen(
    viewModel: MainViewModel,
    uiState: AppState) {
    val context = LocalContext.current

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
                    appCoordinateSystems = false,
                    settings = false,
                    apiKeys = false
                )
            ) { success ->
                Toast.makeText(
                    context,
                    context.getString(if (success) R.string.export_success else R.string.export_failed),
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
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 说明卡：视觉呼应 BlockingScreen，同时说明依然能帮忙采集数据
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIcon(Icons.Rounded.Lock, LabelTone.Error)
                    Spacer(Modifier.width(16.dp))
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
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.data_collection_assist_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 权限缺失提示
            if (missingPermissions.isNotEmpty()) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.data_collection_permission_missing),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
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
                                            Uri.parse("package:${context.packageName}")
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

            // 采集状态与操作卡
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (uiState.isContinuousScanning) Icons.Rounded.Radar else Icons.Rounded.PauseCircle,
                        contentDescription = null,
                        tint = if (uiState.isContinuousScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
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

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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

                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.data_collection_assist_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))

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

            // 导出卡
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
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
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
