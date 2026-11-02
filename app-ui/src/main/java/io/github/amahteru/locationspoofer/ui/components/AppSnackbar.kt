package io.github.amahteru.locationspoofer.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

object AppSnackbar {
    private class Message(val text: String, val long: Boolean)

    private val messages = MutableSharedFlow<Message>(extraBufferCapacity = 16)

    fun show(text: String, long: Boolean = false) {
        messages.tryEmit(Message(text, long))
    }

    @Composable
    fun Host(modifier: Modifier = Modifier) {
        val state = remember { SnackbarHostState() }
        LaunchedEffect(state) {
            messages.collectLatest {
                state.showSnackbar(it.text, duration = if (it.long) SnackbarDuration.Long else SnackbarDuration.Short)
            }
        }
        SnackbarHost(state, modifier)
    }
}
