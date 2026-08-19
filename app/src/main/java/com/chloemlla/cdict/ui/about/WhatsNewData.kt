package com.chloemlla.cdict.ui.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Update
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
            title = "修复卡片阴影伪影",
            subtitle = "词典、推荐、背词、翻译四页的扁平状态卡片显式置零 elevation，移除 Material3 默认阴影在圆角外形成的矩形伪影。",
            bullets = listOf(
                "词典页：加载失败、详情加载中卡片不再投射多余阴影。",
                "推荐/背词页：无法打开词典、已学完、达标、识记等状态卡片阴影移除。",
                "翻译页：骨架屏、失败、加载、结果卡片统一无阴影，状态切换更平滑。",
            ),
            tip = "入口：词典/推荐/背词/翻译各页的错误、加载与完成状态卡片。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "应用内更新检测",
            subtitle = "新增 GitHub Release 更新检测，启动时自动检查新版本并支持应用内下载安装。",
            bullets = listOf(
                "自动检查：应用启动后自动请求 GitHub Releases API，发现新版本时弹窗提示。",
                "手动检查：关于页新增「检查更新」入口，可随时手动触发版本检查。",
                "应用内下载安装：匹配设备 ABI 选择最优 APK，下载时显示进度，校验 SHA256 后引导安装。",
                "安全校验：下载完成后验证 APK 的 SHA256 指纹，确保与发布资产一致。",
            ),
            tip = "入口：关于页 → 检查更新；或启动时自动检测。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "全面优化动画体验",
            subtitle = "词典、翻译、推荐三页新增流畅的过渡动画与微交互反馈，操作更顺滑自然。",
            bullets = listOf(
                "推荐卡片切换：当前卡片操作后新卡片以滑动+淡入过渡进入，不再生硬跳切。",
                "翻译加载骨架屏：翻译等待时显示脉冲骨架占位，替代空白等待；失败时错误卡片轻微抖动提醒。",
                "词典列表入场：词条列表加载/筛选/排序时以交错淡入+微滑动入场，搜索清除时交叉淡出过渡。",
                "进度条与按钮反馈：推荐页进度条平滑动画，主要按钮按压时轻微缩放反馈。",
                "词典详情动画：详情卡片内容变化时平滑过渡，加载底部提示淡入显示。",
            ),
            tip = "入口：词典页搜索与列表；翻译页翻译；推荐页卡片操作。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.BugReport,
            title = "修复队列与搜索竞态",
            subtitle = "修复推荐/背词队列在异步操作后的误删、目标裁剪错误，以及词典搜索结果丢失排序模式、加载更多竞态等问题。",
            bullets = listOf(
                "推荐队列精确移除：consume 改为按词 ID 精确移除，避免数据库写入 suspend 后队列头部已变时误删当前卡片。",
                "目标裁剪修正：推荐页降低每日目标时，已处理的词仍计入目标，不再裁剪过多导致队列短于预期。",
                "背词双击防护：markLearned 在移除队列卡片前检查是否仍在队列中，防止双击导致重复计数。",
                "词典排序保持：搜索结果保留用户当前排序模式，清空搜索后不再重置为默认排序。",
                "加载更多竞态：用列表引用相等替代 size 检查，避免重置后恰好同大小的过期分页被误合并。",
                "内存泄漏修复：短语朗读 ViewModel 改用 ApplicationContext，避免 Activity 被长生命周期组件持有导致泄漏。",
            ),
            tip = "入口：推荐页 / 背词页的卡片操作；词典页搜索与翻页。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "修复推荐页课程标签冷启动",
            subtitle = "修复在推荐页选择「高中短语」等课程标签时，冷启动状态直接显示「已学完」且无法刷新的问题。",
            bullets = listOf(
                "修复推荐引擎冷启动路径：课程标签下的词不在标准雅思频率组 1-7 时，通过全域随机抽样兜底，确保 feed 不为空。",
                "修复后选择「高中短语」等课程标签可正常生成推荐流，不再错误显示「已学完」。",
            ),
            tip = "入口：推荐页 → 顶部筛选 → 选择课程标签。",
        ),
    )
}
