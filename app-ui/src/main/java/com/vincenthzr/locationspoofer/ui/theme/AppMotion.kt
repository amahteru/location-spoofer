package com.vincenthzr.locationspoofer.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

/**
 * M3 标准动效方案（Standard MotionScheme）的弹簧参数，数值取自 Compose Material3 的 StandardMotionTokens。
 * 当前依赖的 material3 1.4.0 中 MotionScheme 尚未公开，故在此按官方 token 等价实现；升级后可直接换成 MaterialTheme.motionScheme。
 *
 * spatial：位移、尺寸、边界变化；effects：透明度、颜色、缩放等不改变布局的效果。
 */
object AppMotion {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)
    // 整页左右滑动专用：临界阻尼不回弹，避免页面滑过头时在屏幕边缘露出底下的地图或页面
    fun <T> pageSlideSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 700f)
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    /** 同级页面整页左右切换：direction 为 1 时新页从右侧滑入，为 -1 时从左侧滑入 */
    fun pageSlideTransition(direction: Int): ContentTransform =
        slideInHorizontally(pageSlideSpec()) { width -> width * direction } togetherWith
            slideOutHorizontally(pageSlideSpec()) { width -> -width * direction }
}

/** 列表项增删、重排的统一动画 */
fun LazyItemScope.animateListItem(): Modifier = Modifier.animateItem(
    fadeInSpec = AppMotion.defaultEffectsSpec(),
    placementSpec = AppMotion.defaultSpatialSpec(),
    fadeOutSpec = AppMotion.fastEffectsSpec()
)
