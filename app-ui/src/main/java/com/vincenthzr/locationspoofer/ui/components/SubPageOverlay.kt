package com.vincenthzr.locationspoofer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vincenthzr.locationspoofer.ui.theme.AppMotion

/**
 * 二级页面覆盖层：覆盖在上级页面之上，从右侧整页滑入、返回时向右滑出（M3 标准位移弹簧）。
 * 需放在与上级页面同一个 Box 中，且位于上级页面之后。
 */
@Composable
fun SubPageOverlay(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(AppMotion.pageSlideSpec()) { width -> width },
        exit = slideOutHorizontally(AppMotion.pageSlideSpec()) { width -> width }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}
