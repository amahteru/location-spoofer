package io.github.amahteru.locationspoofer.ui.screen

import io.github.amahteru.locationspoofer.ui.components.MediumButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.selectLanguage
import androidx.compose.ui.platform.LocalLocale
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

data class LanguageOption(val name: String, val code: String, val nativeName: String)

val LANGUAGES = listOf(
    LanguageOption("英语", "en", "English"),
    LanguageOption("Chinese", "zh", "简体中文")
)

@Composable
fun LanguageSelectionScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val systemLocale = LocalLocale.current.platformLocale.language

    val defaultLang = when {
        systemLocale.startsWith("zh") -> "zh"
        else -> "en"
    }

    var selectedLang by remember { mutableStateOf(defaultLang) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(start = AppSpacing.lg, top = AppSpacing.xl, end = AppSpacing.lg, bottom = AppSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xl, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = "Select Language / 选择语言",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.selectableGroup().padding(vertical = AppSpacing.sm)) {
                LANGUAGES.forEach { lang ->
                    LanguageItem(
                        option = lang,
                        isSelected = selectedLang == lang.code,
                        onClick = { selectedLang = lang.code }
                    )
                }
              }
            }
        }

        Button(
            onClick = {
                viewModel.selectLanguage(selectedLang)
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(
                        selectedLang
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(MediumButtonDefaults.Height),
            contentPadding = MediumButtonDefaults.ContentPadding
        ) {
            Text(
                style = MediumButtonDefaults.TextStyle,
                text = when (selectedLang) {
                    "zh" -> "继续"
                    else -> "Continue"
                }
            )
        }
    }
}

@Composable
fun LanguageItem(
    option: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
        headlineContent = { Text(option.nativeName) },
        supportingContent = { Text(option.name) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
