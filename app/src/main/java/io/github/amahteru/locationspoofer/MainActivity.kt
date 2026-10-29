package io.github.amahteru.locationspoofer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.amahteru.locationspoofer.ui.screen.LanguageSelectionScreen
import io.github.amahteru.locationspoofer.ui.screen.PermissionSetupScreen
import io.github.amahteru.locationspoofer.ui.theme.AppMotion
import io.github.amahteru.locationspoofer.ui.theme.AppTheme
import io.github.amahteru.locationspoofer.utils.LocaleUtils
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.getSavedLanguage
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

        setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
}

private enum class AppStage { Initializing, Language, PermissionSetup, Main }

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    uiState: io.github.amahteru.locationspoofer.data.model.AppState
) {
    val stage = when {
        uiState.isInitializing -> AppStage.Initializing
        !uiState.isLanguageSet -> AppStage.Language
        !uiState.isPermissionSetupDone || !uiState.hasRootAccess || !uiState.isLSPosedActive ->
            AppStage.PermissionSetup
        else -> AppStage.Main
    }
    val slideDistance = with(LocalDensity.current) { 30.dp.roundToPx() }

    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            if (initialState == AppStage.Initializing) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                AppMotion.sharedAxisXTransition(slideDistance)
            }
        },
        label = "app_stage_transition"
    ) { targetStage ->
        when (targetStage) {
            AppStage.Initializing -> Unit
            AppStage.Language -> LanguageSelectionScreen(viewModel)
            AppStage.PermissionSetup -> PermissionSetupScreen(viewModel, uiState)
            AppStage.Main -> io.github.amahteru.locationspoofer.ui.screen.MainScaffoldScreen(
                viewModel = viewModel,
                uiState = uiState
            )
        }
    }
    io.github.amahteru.locationspoofer.ui.screen.CollectionInfoDialogHost(viewModel, uiState)
}
