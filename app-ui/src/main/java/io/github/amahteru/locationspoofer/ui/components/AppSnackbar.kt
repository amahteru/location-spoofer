package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object AppSnackbar {
    private class Message(val text: String, val long: Boolean, val actionLabel: String?, val onAction: (() -> Unit)?)

    private val messages = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    private val state = SnackbarHostState()
    private val hosts = mutableStateListOf<Any>()

    init {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            messages.collectLatest {
                val result = state.showSnackbar(
                    it.text,
                    actionLabel = it.actionLabel,
                    duration = if (it.long || it.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) it.onAction?.invoke()
            }
        }
    }

    fun show(text: String, long: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        messages.tryEmit(Message(text, long, actionLabel, onAction))
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
