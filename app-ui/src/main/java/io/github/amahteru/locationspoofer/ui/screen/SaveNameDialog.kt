package io.github.amahteru.locationspoofer.ui.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import io.github.amahteru.locationspoofer.ui.R

@Composable
fun SaveNameDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    confirmLabel: String = stringResource(R.string.bookmark),
    validate: (String) -> String? = { null }
) {
    var field by remember {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length)))
    }
    val name = field.text.trim()
    val error = validate(name)
    val canConfirm = name.isNotEmpty() && error == null
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                trailingIcon = if (field.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { field = TextFieldValue() }) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null)
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (canConfirm) onConfirm(name) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = canConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
