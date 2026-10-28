package io.github.amahteru.locationspoofer.ui.screen

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.utils.AltitudeModel
import java.util.Locale
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

@Composable
fun AltitudeSettingsCard(
    altitudeInput: String,
    variationM: Int,
    onAltitudeChange: (String) -> Unit,
    onVariationChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var slider by remember(variationM) { mutableFloatStateOf(variationM.toFloat()) }
    val range = slider.toInt()
    val base = altitudeInput.toDoubleOrNull()

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.altitude_simulation),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (base != null) {
                    Text(
                        if (range == 0) {
                            stringResource(R.string.altitude_fixed_value, formatMeters(base))
                        } else {
                            stringResource(
                                R.string.altitude_range_value,
                                formatMeters(base - range),
                                formatMeters(base + range)
                            )
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = altitudeInput,
                onValueChange = onAltitudeChange,
                label = { Text(stringResource(R.string.altitude_meter)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.altitude_variation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (range == 0) stringResource(R.string.altitude_variation_off)
                    else stringResource(R.string.altitude_variation_value, range),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = slider,
                onValueChange = {
                    slider = it
                    onVariationChange(it.toInt())
                },
                valueRange = 0f..AltitudeModel.MAX_VARIATION_M.toFloat(),
                steps = AltitudeModel.MAX_VARIATION_M - 1
            )
            Text(
                stringResource(R.string.altitude_variation_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMeters(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
