package com.vincenthzr.locationspoofer.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.outlined.East
import androidx.compose.material.icons.outlined.North
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CustomCoordinateDialog(
    initialLat: String,
    initialLng: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var lat by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            initialLat
        )
    }
    var lng by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            initialLng
        )
    }

    val currentContext = androidx.compose.ui.platform.LocalContext.current
    val currentConfiguration = androidx.compose.ui.platform.LocalConfiguration.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        title = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides currentContext,
                androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration
            ) {
                androidx.compose.material3.Text(
                    androidx.compose.ui.res.stringResource(com.vincenthzr.locationspoofer.ui.R.string.custom_coordinate_title)
                )
            }
        },
        text = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides currentContext,
                androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration
            ) {
                Column {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(com.vincenthzr.locationspoofer.ui.R.string.custom_coord_desc)
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = lng,
                        onValueChange = { lng = it },
                        label = {
                            androidx.compose.material3.Text(
                                androidx.compose.ui.res.stringResource(
                                    com.vincenthzr.locationspoofer.ui.R.string.longitude
                                )
                            )
                        },
                        placeholder = {
                            androidx.compose.material3.Text(
                                androidx.compose.ui.res.stringResource(
                                    com.vincenthzr.locationspoofer.ui.R.string.coordinate_hint
                                )
                            )
                        },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Outlined.East,
                                null
                            )
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = {
                            androidx.compose.material3.Text(
                                androidx.compose.ui.res.stringResource(
                                    com.vincenthzr.locationspoofer.ui.R.string.latitude
                                )
                            )
                        },
                        placeholder = {
                            androidx.compose.material3.Text(
                                androidx.compose.ui.res.stringResource(
                                    com.vincenthzr.locationspoofer.ui.R.string.coordinate_hint
                                )
                            )
                        },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Outlined.North,
                                null
                            )
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides currentContext,
                androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration
            ) {
                androidx.compose.material3.TextButton(onClick = { onConfirm(lat, lng) }) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(com.vincenthzr.locationspoofer.ui.R.string.confirm)
                    )
                }
            }
        },
        dismissButton = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides currentContext,
                androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(com.vincenthzr.locationspoofer.ui.R.string.cancel)
                    )
                }
            }
        }
    )
}
