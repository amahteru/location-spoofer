package com.vincenthzr.locationspoofer.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * M3 标准动效方案（Standard MotionScheme）的弹簧参数，数值取自 Compose Material3 的 StandardMotionTokens。
 * 当前依赖的 material3 1.4.0 中 MotionScheme 尚未公开，故在此按官方 token 等价实现；升级后可直接换成 MaterialTheme.motionScheme。
 *
 * spatial：位移、尺寸、边界变化；effects：透明度、颜色、缩放等不改变布局的效果。
 */
object AppMotion {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}
