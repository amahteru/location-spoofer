package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppSnackbar {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val state = SnackbarHostState()
    private val hosts = mutableStateListOf<Any>()

    var bottomInset by mutableStateOf(0.dp)
        private set

    fun show(text: String, long: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            val result = state.showSnackbar(
                text,
                actionLabel = actionLabel,
                duration = if (long || actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    @Composable
    fun BottomInset(height: Dp) {
        DisposableEffect(height) {
            bottomInset = height
            onDispose { if (bottomInset == height) bottomInset = 0.dp }
        }
    }

    @Composable
    fun Host(modifier: Modifier = Modifier) {
        val token = remember { Any() }
        DisposableEffect(token) {
            hosts.add(token)
            onDispose { hosts.remove(token) }
        }
        if (hosts.lastOrNull() === token) SnackbarHost(state, modifier)
    }
}
