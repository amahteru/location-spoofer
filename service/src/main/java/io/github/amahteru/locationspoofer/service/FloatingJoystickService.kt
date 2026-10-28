package io.github.amahteru.locationspoofer.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.amahteru.locationspoofer.data.motion.MotionController
import io.github.amahteru.locationspoofer.ui.theme.AppTypography
import io.github.amahteru.locationspoofer.ui.theme.appColorScheme
import io.github.amahteru.locationspoofer.ui.theme.onSuccess
import io.github.amahteru.locationspoofer.ui.theme.onWarning
import io.github.amahteru.locationspoofer.ui.theme.success
import io.github.amahteru.locationspoofer.ui.theme.warning
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import io.github.amahteru.locationspoofer.ui.theme.AppSpacing

class FloatingJoystickService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val motionController: MotionController by inject()

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var composeView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        isShowing = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycleScope.launch {
            motionController.state.collect {
                if (!it.hasRoute && !it.hasPendingRecording) stopSelf()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow() {
        val density = resources.displayMetrics.density
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (160 * density).toInt()
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingJoystickService)
            setViewTreeViewModelStoreOwner(this@FloatingJoystickService)
            setViewTreeSavedStateRegistryOwner(this@FloatingJoystickService)
            setContent {
                MaterialTheme(
                    colorScheme = appColorScheme(this@FloatingJoystickService, isSystemInDarkTheme()),
                    typography = AppTypography
                ) {
                    val state by motionController.state.collectAsState()
                    FloatingJoystickOverlay(
                        state = state,
                        onMoveWindow = { dx, dy -> moveWindow(dx, dy) },
                        onJoystick = { bearing, intensity -> motionController.joystick(bearing, intensity) },
                        onPause = { motionController.pause() },
                        onResume = { motionController.resume() },
                        onPreset = { motionController.setSpeedPreset(it) },
                        onStartRecording = { motionController.startRecording() },
                        onStopRecording = { motionController.stopRecording() },
                        onSaveRecording = { name -> saveRecording(name) },
                        onDiscardRecording = { motionController.discardRecording() },
                        onNeedKeyboard = { setFocusable(it) }
                    )
                }
            }
        }
        windowManager.addView(composeView, params)
    }

    private fun moveWindow(dx: Float, dy: Float) {
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        composeView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun setFocusable(focusable: Boolean) {
        val flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (flags == params.flags) return
        params.flags = flags
        composeView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun saveRecording(name: String) {
        lifecycleScope.launch {
            val saved = motionController.saveRecording(name)
            if (saved) Toast.makeText(this@FloatingJoystickService, R.string.fj_saved_toast, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        isShowing = false
        motionController.joystick(0.0, 0f)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.let { windowManager.removeView(it) }
        composeView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var isShowing = false
            private set
    }
}

private data class Palette(
    val card: Color,
    val border: Color,
    val variant: Color,
    val segment: Color,
    val onSegment: Color,
    val text: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val red: Color,
    val onRed: Color,
    val redContainer: Color,
    val onRedContainer: Color
)

private fun ColorScheme.toPalette() = Palette(
    card = surfaceContainer,
    border = outlineVariant,
    variant = surfaceContainerHighest,
    segment = secondaryContainer,
    onSegment = onSecondaryContainer,
    text = onSurface,
    textSecondary = onSurfaceVariant,
    primary = primary,
    onPrimary = onPrimary,
    success = success,
    onSuccess = onSuccess,
    warning = warning,
    onWarning = onWarning,
    red = error,
    onRed = onError,
    redContainer = errorContainer,
    onRedContainer = onErrorContainer
)

private fun statusColor(state: MotionController.State, palette: Palette): Color = when {
    !state.active -> palette.textSecondary
    state.returning -> palette.primary
    state.paused -> palette.warning
    state.mode == MotionController.Mode.ROUTE -> palette.success
    else -> palette.primary
}

@Composable
private fun FloatingJoystickOverlay(
    state: MotionController.State,
    onMoveWindow: (Float, Float) -> Unit,
    onJoystick: (Double, Float) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPreset: (MotionController.SpeedPreset) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveRecording: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onNeedKeyboard: (Boolean) -> Unit
) {
    val palette = MaterialTheme.colorScheme.toPalette()
    var collapsed by remember { mutableStateOf(false) }
    LaunchedEffect(state.hasPendingRecording) {
        if (state.hasPendingRecording) collapsed = false
        onNeedKeyboard(state.hasPendingRecording)
    }
    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, drag ->
            change.consume()
            onMoveWindow(drag.x, drag.y)
        }
    }

    if (collapsed) {
        CollapsedBubble(state, palette, dragModifier) { collapsed = false }
        return
    }

    Column(
        modifier = Modifier
            .padding(AppSpacing.md)
            .width(208.dp)
            .shadow(6.dp, MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(palette.card)
            .border(1.dp, palette.border.copy(alpha = 0.6f), MaterialTheme.shapes.extraLarge)
            .padding(start = AppSpacing.md, end = AppSpacing.md, bottom = AppSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .then(dragModifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.textSecondary.copy(alpha = 0.4f))
            )
        }

        if (state.hasPendingRecording) {
            SaveRecordingForm(palette, onSaveRecording, onDiscardRecording)
            return@Column
        }

        Joystick(enabled = state.joystickEnabled, palette = palette, onJoystick = onJoystick)
        SpeedSegments(state, palette, onPreset)
        ActionTabs(state, palette, onPause, onResume, onStartRecording, onStopRecording, onCollapse = { collapsed = true })
    }
}

@Composable
private fun CollapsedBubble(state: MotionController.State, palette: Palette, modifier: Modifier, onExpand: () -> Unit) {
    Box(modifier = Modifier.padding(AppSpacing.md)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(palette.card)
                .border(2.dp, statusColor(state, palette), CircleShape)
                .then(modifier)
                .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Gamepad,
                stringResource(R.string.floating_joystick_title),
                tint = palette.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        if (state.recording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(palette.card)
                    .padding(AppSpacing.xs)
                    .clip(CircleShape)
                    .background(palette.red)
            )
        }
    }
}

@Composable
private fun Joystick(enabled: Boolean, palette: Palette, onJoystick: (Double, Float) -> Unit) {
    val baseSize = 120.dp
    val thumbSize = 44.dp
    val maxRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { ((baseSize - thumbSize) / 2).toPx() }
    var thumb by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(enabled) {
        if (!enabled) thumb = Offset.Zero
    }

    Box(
        modifier = Modifier
            .size(baseSize)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(palette.variant)
            .border(1.dp, palette.border.copy(alpha = 0.5f), CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        thumb = Offset.Zero
                        onJoystick(0.0, 0f)
                    },
                    onDragCancel = {
                        thumb = Offset.Zero
                        onJoystick(0.0, 0f)
                    }
                ) { change, drag ->
                    change.consume()
                    val raw = thumb + drag
                    val dist = sqrt(raw.x * raw.x + raw.y * raw.y)
                    thumb = if (dist <= maxRadiusPx) raw else raw * (maxRadiusPx / dist)
                    val angle = atan2(thumb.y.toDouble(), thumb.x.toDouble())
                    val intensity = (sqrt(thumb.x * thumb.x + thumb.y * thumb.y) / maxRadiusPx).coerceIn(0f, 1f)
                    onJoystick((Math.toDegrees(angle) + 90 + 360) % 360, intensity)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        listOf(Alignment.TopCenter, Alignment.BottomCenter, Alignment.CenterStart, Alignment.CenterEnd).forEach { align ->
            Box(
                modifier = Modifier
                    .align(align)
                    .padding(AppSpacing.sm)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(palette.textSecondary.copy(alpha = 0.45f))
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(thumb.x.roundToInt(), thumb.y.roundToInt()) }
                .size(thumbSize)
                .shadow(if (enabled) 6.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(palette.primary),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(palette.onPrimary.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
private fun SpeedSegments(state: MotionController.State, palette: Palette, onPreset: (MotionController.SpeedPreset) -> Unit) {
    val labels = mapOf(
        "WALKING" to stringResource(R.string.fj_preset_walking),
        "RUNNING" to stringResource(R.string.fj_preset_running),
        "CYCLING" to stringResource(R.string.fj_preset_cycling),
        "DRIVING" to stringResource(R.string.fj_preset_driving)
    )
    val presets = MotionController.PRESETS +
        listOfNotNull(state.preset.takeIf { p -> MotionController.PRESETS.none { it.name == p.name } })
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(palette.variant)
            .padding(AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        presets.forEach { preset ->
            val selected = preset.name == state.preset.name
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (selected) palette.segment else Color.Transparent)
                    .clickable(enabled = state.active) { onPreset(preset) }
                    .padding(vertical = AppSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    labels[preset.name] ?: stringResource(R.string.fj_preset_custom),
                    color = if (selected) palette.onSegment else palette.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ActionTabs(
    state: MotionController.State,
    palette: Palette,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCollapse: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        if (state.hasRoute) {
            when {
                state.paused -> TabButton(
                    Icons.Rounded.PlayArrow, stringResource(R.string.fj_resume),
                    container = palette.success, content = palette.onSuccess,
                    enabled = state.active && !state.returning, onClick = onResume
                )
                else -> TabButton(
                    Icons.Rounded.Pause, stringResource(R.string.fj_pause),
                    container = palette.warning, content = palette.onWarning,
                    enabled = state.active && !state.returning, onClick = onPause
                )
            }
        }
        if (state.recording) {
            TabButton(
                Icons.Rounded.Stop, stringResource(R.string.fj_stop_record),
                container = palette.red, content = palette.onRed, enabled = true, onClick = onStopRecording
            )
        } else {
            TabButton(
                Icons.Rounded.FiberManualRecord, stringResource(R.string.fj_record),
                container = palette.redContainer, content = palette.onRedContainer,
                enabled = state.active, onClick = onStartRecording
            )
        }
        TabButton(
            Icons.Rounded.Remove, stringResource(R.string.fj_collapse),
            container = palette.variant, content = palette.textSecondary, enabled = true, onClick = onCollapse
        )
    }
}

@Composable
private fun RowScope.TabButton(
    icon: ImageVector,
    description: String,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = content, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SaveRecordingForm(palette: Palette, onSave: (String) -> Unit, onDiscard: () -> Unit) {
    val defaultName = stringResource(
        R.string.fj_default_route_name,
        SimpleDateFormat("MM-dd HH:mm", LocalConfiguration.current.locales[0]).format(Date())
    )
    var name by remember { mutableStateOf(defaultName) }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.fj_route_name_hint)) },
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            focusedBorderColor = palette.primary,
            unfocusedBorderColor = palette.border,
            cursorColor = palette.primary
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        TabButton(
            Icons.Rounded.Close, stringResource(R.string.fj_discard),
            container = palette.variant, content = palette.textSecondary, enabled = true, onClick = onDiscard
        )
        TabButton(
            Icons.Rounded.Check, stringResource(R.string.fj_save),
            container = palette.primary, content = palette.onPrimary, enabled = name.isNotBlank(), onClick = { onSave(name) }
        )
    }
}
