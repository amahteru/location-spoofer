package io.github.amahteru.locationspoofer.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

object AppMotion {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)
    fun <T> pageSlideSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 700f)
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    fun pageSlideTransition(direction: Int): ContentTransform =
        slideInHorizontally(pageSlideSpec()) { width -> width * direction } togetherWith
            slideOutHorizontally(pageSlideSpec()) { width -> -width * direction }

    fun sharedAxisXTransition(slideDistance: Int): ContentTransform =
        (slideInHorizontally(defaultSpatialSpec()) { slideDistance } + fadeIn(defaultEffectsSpec())) togetherWith
            (slideOutHorizontally(defaultSpatialSpec()) { -slideDistance } + fadeOut(fastEffectsSpec()))
}

fun Modifier.animateListItem(scope: LazyItemScope): Modifier = with(scope) {
    this@animateListItem.animateItem(
        fadeInSpec = AppMotion.defaultEffectsSpec(),
        placementSpec = AppMotion.defaultSpatialSpec(),
        fadeOutSpec = AppMotion.fastEffectsSpec()
    )
}
