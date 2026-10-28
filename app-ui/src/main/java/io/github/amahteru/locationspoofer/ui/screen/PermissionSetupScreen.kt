package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.components.MediumButtonDefaults
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.components.LabelTone
import io.github.amahteru.locationspoofer.ui.components.ToneIcon
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.completePermissionSetup
import io.github.amahteru.locationspoofer.viewmodel.recheckRootAccess

private enum class AppPermission(
    val icon: ImageVector,
    val tone: LabelTone,
    val titleRes: Int,
    val descRes: Int,
    val runtime: List<String> = emptyList()
) {
    LOCATION(
        Icons.Rounded.LocationOn, LabelTone.Primary,
        R.string.perm_location_title, R.string.perm_location_desc,
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ),
    NOTIFICATION(
        Icons.Rounded.Notifications, LabelTone.Primary,
        R.string.perm_notification_title, R.string.perm_notification_desc,
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    ),
    NEARBY(
        Icons.Rounded.Bluetooth, LabelTone.Tertiary,
        R.string.perm_nearby_title, R.string.perm_nearby_desc,
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    ),
    PHONE(
        Icons.Rounded.Phone, LabelTone.Tertiary,
        R.string.perm_phone_title, R.string.perm_phone_desc,
        listOf(Manifest.permission.READ_PHONE_STATE)
    ),
    BACKGROUND_LOCATION(
        Icons.Rounded.MyLocation, LabelTone.Tertiary,
        R.string.perm_background_location_title, R.string.perm_background_location_desc,
        listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    ),
    OVERLAY(
        Icons.Rounded.Gamepad, LabelTone.Secondary,
        R.string.perm_overlay_title, R.string.perm_overlay_desc
    ),
    BATTERY(
        Icons.Rounded.BatteryChargingFull, LabelTone.Warning,
        R.string.ignore_battery_optimizations_title, R.string.perm_battery_desc
    );

    fun isGranted(context: Context): Boolean = when (this) {
        OVERLAY -> Settings.canDrawOverlays(context)
        BATTERY -> (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
        else -> runtime.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}

private val CORE_PERMISSIONS = listOf(AppPermission.LOCATION, AppPermission.NOTIFICATION)
private val OPTIONAL_PERMISSIONS = listOf(
    AppPermission.NEARBY,
    AppPermission.PHONE,
    AppPermission.BACKGROUND_LOCATION,
    AppPermission.OVERLAY,
    AppPermission.BATTERY
)

@Composable
fun PermissionSetupScreen(viewModel: MainViewModel, uiState: AppState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val environmentReady = uiState.hasRootAccess && uiState.isLSPosedActive
    var checkingRoot by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    fun snapshot() = AppPermission.entries.filter { it.isGranted(context) }.toSet()

    var granted by remember { mutableStateOf(snapshot()) }
    var requested by rememberSaveable { mutableStateOf(emptySet<String>()) }

    LifecycleResumeEffect(Unit) {
        granted = snapshot()
        onPauseOrDispose { }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = snapshot()
    }

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            )
        }
    }

    fun isBlocked(permission: AppPermission): Boolean =
        permission.runtime.isNotEmpty() && permission.name in requested &&
            permission.runtime.none { activity?.shouldShowRequestPermissionRationale(it) == true }

    @android.annotation.SuppressLint("BatteryLife")
    fun request(permission: AppPermission) {
        when {
            permission == AppPermission.OVERLAY -> openSettings(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
            )
            permission == AppPermission.BATTERY -> openSettings(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
            )
            isBlocked(permission) -> openSettings(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            )
            else -> {
                requested = requested + permission.name
                runtimeLauncher.launch(permission.runtime.toTypedArray())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.permission_setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.permission_setup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            EnvironmentGroup(
                hasRoot = uiState.hasRootAccess,
                isLSPosedActive = uiState.isLSPosedActive,
                checkingRoot = checkingRoot,
                onCheckRoot = {
                    checkingRoot = true
                    viewModel.recheckRootAccess { root ->
                        checkingRoot = false
                        if (!root) {
                            android.widget.Toast.makeText(
                                context,
                                resources.getString(R.string.env_root_denied),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onRestart = {
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launch?.component != null) {
                        context.startActivity(Intent.makeRestartActivityTask(launch.component))
                        Runtime.getRuntime().exit(0)
                    }
                }
            )
            PermissionGroup(
                title = stringResource(R.string.permission_group_core),
                permissions = CORE_PERMISSIONS,
                granted = granted,
                isBlocked = ::isBlocked,
                onRequest = ::request
            )
            PermissionGroup(
                title = stringResource(R.string.permission_group_optional),
                permissions = OPTIONAL_PERMISSIONS,
                granted = granted,
                isBlocked = ::isBlocked,
                onRequest = ::request
            )
        }

        Button(
            onClick = { viewModel.completePermissionSetup() },
            enabled = environmentReady,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
                .height(MediumButtonDefaults.Height),
            contentPadding = MediumButtonDefaults.ContentPadding
        ) {
            Text(stringResource(R.string.permission_continue), style = MediumButtonDefaults.TextStyle)
        }
    }
}

@Composable
private fun EnvironmentGroup(
    hasRoot: Boolean,
    isLSPosedActive: Boolean,
    checkingRoot: Boolean,
    onCheckRoot: () -> Unit,
    onRestart: () -> Unit
) {
    GroupCard(title = stringResource(R.string.permission_group_environment)) {
        ListItem(
            leadingContent = { ToneIcon(Icons.Rounded.Tag, LabelTone.Error) },
            headlineContent = { Text(stringResource(R.string.env_root_title)) },
            supportingContent = { Text(stringResource(R.string.env_root_desc)) },
            trailingContent = {
                if (hasRoot) {
                    GrantedIcon()
                } else {
                    FilledTonalButton(onClick = { if (!checkingRoot) onCheckRoot() }) {
                        Text(stringResource(R.string.env_check))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        ListItem(
            leadingContent = { ToneIcon(Icons.Rounded.Extension, LabelTone.Error) },
            headlineContent = { Text(stringResource(R.string.env_lsposed_title)) },
            supportingContent = { Text(stringResource(R.string.env_lsposed_desc)) },
            trailingContent = {
                if (isLSPosedActive) {
                    GrantedIcon()
                } else {
                    FilledTonalButton(onClick = onRestart) {
                        Text(stringResource(R.string.env_restart))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun GroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = AppSpacing.lg)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = AppSpacing.sm), content = content)
        }
    }
}

@Composable
private fun GrantedIcon() {
    Icon(
        Icons.Rounded.CheckCircle,
        contentDescription = stringResource(R.string.permission_granted),
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PermissionGroup(
    title: String,
    permissions: List<AppPermission>,
    granted: Set<AppPermission>,
    isBlocked: (AppPermission) -> Boolean,
    onRequest: (AppPermission) -> Unit
) {
    GroupCard(title = title) {
        permissions.forEach { permission ->
            val enabled = permission != AppPermission.BACKGROUND_LOCATION ||
                AppPermission.LOCATION in granted
            PermissionItem(
                permission = permission,
                isGranted = permission in granted,
                isBlocked = isBlocked(permission),
                enabled = enabled,
                onRequest = { onRequest(permission) }
            )
        }
    }
}

@Composable
private fun PermissionItem(
    permission: AppPermission,
    isGranted: Boolean,
    isBlocked: Boolean,
    enabled: Boolean,
    onRequest: () -> Unit
) {
    ListItem(
        leadingContent = { ToneIcon(permission.icon, permission.tone) },
        headlineContent = { Text(stringResource(permission.titleRes)) },
        supportingContent = {
            Text(stringResource(if (enabled) permission.descRes else R.string.perm_background_location_requires))
        },
        trailingContent = {
            if (isGranted) {
                GrantedIcon()
            } else {
                FilledTonalButton(onClick = onRequest, enabled = enabled) {
                    Text(
                        stringResource(if (isBlocked) R.string.go_to_settings else R.string.permission_allow)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
