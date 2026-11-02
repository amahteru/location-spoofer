package io.github.amahteru.locationspoofer.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.style.LibraryActionBadges
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.components.LabelTone
import io.github.amahteru.locationspoofer.ui.components.NavigationListItem
import io.github.amahteru.locationspoofer.ui.components.SectionTitle
import io.github.amahteru.locationspoofer.ui.components.SubPageScaffold
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun LicensesScreen(@RawRes librariesRes: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val uriHandler = LocalUriHandler.current
    val libraries by produceLibraries(librariesRes)

    SubPageScaffold(
        title = stringResource(R.string.licenses_title),
        onBack = onClose,
        scrollable = false
    ) {
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.weight(1f),
            actionLabels = LibraryActionBadges(
                source = stringResource(R.string.licenses_action_source),
                website = stringResource(R.string.licenses_action_website),
                sponsor = stringResource(R.string.licenses_action_sponsor),
                viewLicense = stringResource(R.string.licenses_action_view_license)
            ),
            header = {
                item {
                    SectionTitle(stringResource(R.string.licenses_project_section))
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm)) {
                        NavigationListItem(
                            icon = Icons.Rounded.Gavel,
                            title = stringResource(R.string.licenses_project_title),
                            summary = stringResource(R.string.licenses_project_desc),
                            tone = LabelTone.Primary,
                            onClick = { uriHandler.openUri("https://github.com/amahteru/location-spoofer/blob/main/LICENSE") },
                            trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) }
                        )
                    }
                }
                item {
                    SectionTitle(stringResource(R.string.licenses_third_party_section))
                }
            }
        )
    }
}
