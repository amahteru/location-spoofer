package com.vincenthzr.locationspoofer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * 标签 + 单行输入框 + 清空按钮的设置项输入组件（M3 Outlined Text Field）。被"地图引擎与密钥"和
 * "环境数据源 Token"两个设置页共用，因此提升为公共组件，不能各自复制一份。
 */
@Composable
fun ModernSettingsInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Cancel, contentDescription = null)
                }
            }
        } else null,
        modifier = modifier.fillMaxWidth()
    )
}
