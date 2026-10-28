package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.amahteru.locationspoofer.data.db.CompleteCollectionRoute
import io.github.amahteru.locationspoofer.ui.R

@Composable
fun CollectionRouteListItem(item: CompleteCollectionRoute, onClick: () -> Unit, onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { ToneIcon(Icons.Outlined.Route, LabelTone.Primary) },
            headlineContent = { Text(item.route.name.ifBlank { stringResource(R.string.collection_route_unnamed) }) },
            supportingContent = {
                Column {
                    if (item.route.remark.isNotBlank()) Text(item.route.remark)
                    Text(stringResource(R.string.collection_route_summary, item.samples.size, item.route.halfWidthM.toInt()))
                }
            },
            trailingContent = if (onEdit != null || onDelete != null) {
                {
                    Row {
                        onEdit?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit)) } }
                        onDelete?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) } }
                    }
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
