package com.chloemlla.cdict.ui.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.NewReleases
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
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "朗读按钮支持停止切换与状态反馈",
            subtitle = "词典、推荐、背词三页共用一套朗读组件；释义 / 句子 / 搭配等小喇叭按钮同样支持播放中停止并显示「停止」图标。",
            bullets = listOf(
                "共用组件：词典详情、推荐卡片、背词卡片统一使用同一套英音/美音按钮，一处维护、三页同步。",
                "停止与状态反馈：播放中英音/美音按钮显示「停止」图标与文字，点击即可停止播放。",
                "释义朗读停止：词条释义、真题句子、常见搭配等英文段落的小喇叭按钮在播放中显示「停止」图标，点击可停止播放。",
                "状态自动更新：朗读播放完成后按钮状态自动复位，不会残留错误的播放状态。",
            ),
            tip = "入口：词典词详情 → 朗读；推荐页 / 背词页卡片上的英音美音按钮；释义 / 例句 / 搭配的小喇叭按钮。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "优化复习体验与按钮反馈",
            subtitle = "缩短复习识记卡延迟、增加触觉反馈、背词按钮加入加载状态。",
            bullets = listOf(
                "识记卡延迟优化：复习模式下完全陌生词的释义展示延迟从 3 秒缩短至 1.5 秒，减少等待时间。",
                "触觉反馈：复习答题正确时轻触反馈、错误时重触提醒，配合提示音强化学习体感。",
                "按钮加载状态：词典详情页的「加入背词计划」按钮在操作执行期间显示加载动画，防止重复点击。",
            ),
            tip = "入口：背词标签页 → 复习识记卡；词典页 → 词详情 → 背词计划按钮。",
        ),
    )
}
