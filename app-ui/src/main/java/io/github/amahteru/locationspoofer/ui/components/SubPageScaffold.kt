package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                AppTopBar(title = title, onBack = onBack, subtitle = subtitle, actions = actions)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            content = {
                content()
                Spacer(Modifier.height(AppSpacing.sm))
            }
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = AppSpacing.xs, top = AppSpacing.sm)
    )
}
