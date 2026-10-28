package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 0.dp,
    onSearch: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SearchBarDefaults.inputFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = tonalElevation
    ) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {
                onSearch(it)
                focusManager.clearFocus()
            },
            expanded = false,
            onExpandedChange = {},
            placeholder = { Text(placeholder, maxLines = 1) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            } else null
        )
    }
}
