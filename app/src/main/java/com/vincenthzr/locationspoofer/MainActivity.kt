package com.vincenthzr.locationspoofer

import androidx.core.net.toUri
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.screen.BlockingScreen
import com.vincenthzr.locationspoofer.ui.screen.LanguageSelectionScreen
import com.vincenthzr.locationspoofer.ui.theme.AppTheme
import com.vincenthzr.locationspoofer.utils.LocaleUtils
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.getSavedLanguage
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isLangSet = prefs.getBoolean("is_language_set", false)
        val lang = if (isLangSet) LocaleUtils.normalizeLanguage(prefs.getString("language", "") ?: "") else ""

        val context = if (lang.isNotEmpty()) {
            LocaleUtils.wrap(newBase, lang)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val savedLang = viewModel.getSavedLanguage()
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (savedLang.isNotEmpty() && (currentLocales.isEmpty || currentLocales.toLanguageTags() != savedLang)) {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(savedLang)
            )
        }

        overlayPromptHandled = savedInstanceState != null
        checkAndRequestPermissions()

        setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                val uiState by viewModel.uiState.collectAsState()
                val isDark = isSystemInDarkTheme()
                val baseDensity = LocalDensity.current
                val appDensity = remember(baseDensity.density, baseDensity.fontScale) {
                    Density(baseDensity.density, baseDensity.fontScale.coerceAtMost(1.15f))
                }

                val context = LocalContext.current
                val wrappedContext = remember(uiState.currentLanguage) {
                    if (uiState.currentLanguage.isNotEmpty()) {
                        android.view.ContextThemeWrapper(context, context.theme).apply {
                            applyOverrideConfiguration(LocaleUtils.wrap(context, uiState.currentLanguage).resources.configuration)
                        }
                    } else {
                        context
                    }
                }
                val configuration = wrappedContext.resources.configuration

                CompositionLocalProvider(
                    LocalContext provides wrappedContext,
                    LocalConfiguration provides configuration,
                    LocalDensity provides appDensity
                ) {
                    AppTheme(isDark = isDark) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            MainScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val notGranted = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissions(notGranted.toTypedArray(), 100)
        } else {
            checkBackgroundLocation()
            requestOverlayPermissionIfNeeded()
            requestIgnoreBatteryOptimizations()
        }
    }

    private var overlayPromptHandled = false

    private fun requestOverlayPermissionIfNeeded() {
        if (overlayPromptHandled || android.provider.Settings.canDrawOverlays(this)) return
        overlayPromptHandled = true
        android.widget.Toast.makeText(
            this,
            getString(com.vincenthzr.locationspoofer.ui.R.string.overlay_permission_on_launch),
            android.widget.Toast.LENGTH_LONG
        ).show()
        try {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun checkBackgroundLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 101)
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) checkBackgroundLocation()
            requestOverlayPermissionIfNeeded()
            if (granted) requestIgnoreBatteryOptimizations()
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    uiState: com.vincenthzr.locationspoofer.data.model.AppState
) {
    when {
        uiState.isInitializing -> Unit
        !uiState.isLanguageSet -> LanguageSelectionScreen(viewModel)
        !uiState.hasRootAccess -> com.vincenthzr.locationspoofer.ui.screen.DataCollectionAssistScreen(
            viewModel = viewModel,
            uiState = uiState
        )
        !uiState.isLSPosedActive -> BlockingScreen(
            icon = Icons.Rounded.Extension,
            title = stringResource(R.string.lsposed_not_active),
            message = stringResource(R.string.lsposed_message))
        else -> com.vincenthzr.locationspoofer.ui.screen.MainScaffoldScreen(
            viewModel = viewModel,
            uiState = uiState
        )
    }
    com.vincenthzr.locationspoofer.ui.screen.CollectionInfoDialogHost(viewModel, uiState)
}
