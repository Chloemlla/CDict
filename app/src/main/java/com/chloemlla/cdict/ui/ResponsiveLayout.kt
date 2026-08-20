package com.chloemlla.cdict.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 按窗口宽度尺寸类选择的最大内容行宽；无上限（手机竖屏）时返回 [Dp.Infinity]。 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun responsiveContentMaxWidth(): Dp {
    val activity = LocalContext.current.findActivity()
    if (activity == null) return Dp.Infinity
    return when (calculateWindowSizeClass(activity).widthSizeClass) {
        WindowWidthSizeClass.Medium -> 600.dp
        WindowWidthSizeClass.Expanded -> 840.dp
        else -> Dp.Infinity
    }
}

/**
 * 【响应式】把 [content] 居中约束为可读行宽：手机竖屏(COMPACT)保持满宽；
 * 平板竖屏/折叠(MEDIUM)限宽约 600dp；平板横屏/大屏(EXPANDED)限宽约 840dp。
 * 子内容请填充整块（如 Column(Modifier.fillMaxSize())）以随高度分布。
 */
@Composable
fun ResponsiveContentBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val maxWidth = responsiveContentMaxWidth()
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(
                    if (maxWidth == Dp.Infinity) Modifier.fillMaxWidth()
                    else Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                ),
            content = content,
        )
    }
}

/**
 * 【响应式】窗口高度是否为紧凑档（手机横屏、分屏等，约 < 480dp）。
 * 这类窗口留给内容的高度只有一两百 dp，固定在顶部的搜索框与筛选行会把用 weight 分配
 * 高度的列表挤到近乎 0，因此紧凑高度下应把这些顶部控件放进列表一起滚动。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun isCompactWindowHeight(): Boolean {
    val activity = LocalContext.current.findActivity() ?: return false
    return calculateWindowSizeClass(activity).heightSizeClass == WindowHeightSizeClass.Compact
}

/** 从 Compose LocalContext 逆包装找到 [Activity]；找不到返回 null（退化为满宽）。 */
internal fun Context.findActivity(): Activity? {
    if (this is Activity) return this
    if (this !is ContextWrapper) return null
    return baseContext.findActivity()
}