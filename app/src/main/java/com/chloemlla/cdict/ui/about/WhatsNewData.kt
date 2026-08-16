package com.chloemlla.cdict.ui.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.ui.graphics.vector.ImageVector

data class WhatsNewSlide(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
    val tip: String? = null,
)

object WhatsNewData {
    fun slides(): List<WhatsNewSlide> = listOf(
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "本次构建更新说明",
            subtitle = "本构建包含以下有意变更，基于 Commit Hash / Build Time 标识。",
            bullets = listOf(
                "版本：${BuildInfo.versionLabel}",
                "Build Time：${BuildInfo.formatBuildTime()}",
                "Commit Hash：${BuildInfo.commitHash.take(12)}",
                "与「应用声明」等静态页面不同：这里讲的是这次新构建相对上一构建的变化。",
            ),
            tip = "可左右滑动浏览；同一构建确认后不会再次自动弹出。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.MenuBook,
            title = "词详情页派生词「前往」跳转",
            subtitle = "词详情页的派生词条目新增「前往」入口，可跳转到对应词条的完整详情。",
            bullets = listOf(
                "与发音按钮对齐，命中目标行样式保持一致。",
                "返回时回到词详情页的来源位置。",
            ),
            tip = "入口：词详情页 → 派生词列表 → 前往。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.Construction,
            title = "构建工具链升级",
            subtitle = "构建工具链由 Java 17 升级到 Java 21，并修正 Room 元数据实体与词典库 schema 对齐。",
            bullets = listOf(
                "Java 17 → Java 21（JVM toolchain 21）。",
                "MetadataEntity 与词典库 schema 对齐，DAO 元数据查询可编译。",
            ),
            tip = "对日常使用无感；属于工程加固。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.RocketLaunch,
            title = "可以继续使用了",
            subtitle = "以上是本构建值得知道的有意变更。之后同一 Commit / Build Time 不会再自动弹出。",
            bullets = listOf(
                "可在「关于 → 本次更新说明」再次打开。",
                "开源协议与第三方鸣谢见「应用声明 → 开源许可声明」。",
                "应用权限见「应用声明 → 应用权限」。",
            ),
            tip = "点「知道了」进入应用。",
        ),
    )
}
