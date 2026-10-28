package io.github.amahteru.locationspoofer.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.os.LocaleListCompat
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.data.model.AppState
import io.github.amahteru.locationspoofer.ui.components.SubPageScaffold
import io.github.amahteru.locationspoofer.ui.screen.LANGUAGES
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.getSavedLanguage
import io.github.amahteru.locationspoofer.viewmodel.selectLanguage
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun LanguageSettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    SubPageScaffold(
        title = stringResource(R.string.select_language),
        subtitle = stringResource(R.string.switch_language_desc),
        onBack = onClose
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.selectableGroup().padding(vertical = AppSpacing.sm)) {
                LANGUAGES.forEach { lang ->
                    val isSelected = viewModel.getSavedLanguage() == lang.code
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                viewModel.selectLanguage(lang.code)
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(lang.code)
                                )
                            }
                        ),
                        headlineContent = { Text(lang.nativeName) },
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
