package com.vincenthzr.locationspoofer.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全 app 统一的间距表：MD3 以 8dp 为基准，4dp 作为小元素内部的半步。
 * 外边距、留白、元素间隔一律从这里取，不再直接写 dp 数字，避免出现 10dp、14dp 这类不在网格上的值。
 * 主界面与悬浮摇杆窗共用，故放在两者都依赖的 core-data 模块。
 */
object AppSpacing {
    /** 4dp：图标与文字、标题与副标题等紧挨着的元素 */
    val xs = 4.dp
    /** 8dp：同组元素之间（按钮与按钮、列表项内部） */
    val sm = 8.dp
    /** 12dp：卡片内的分组、小卡片内边距 */
    val md = 12.dp
    /** 16dp：卡片内边距、区块之间 */
    val lg = 16.dp
    /** 24dp：大区块之间、页面底部留白 */
    val xl = 24.dp
    /** 32dp：页面级的大间隔 */
    val xxl = 32.dp

    /** 手机窄屏的左右边距（MD3 compact 窗口为 16dp） */
    val screenMargin = lg

    /** 底部操作面板的按钮高度，各阶段统一，切换时面板不跳动 */
    val actionButtonHeight = 48.dp
}
