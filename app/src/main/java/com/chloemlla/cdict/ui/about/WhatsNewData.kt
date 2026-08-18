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
        WhatsNewSlide(
            icon = Icons.Filled.BugReport,
            title = "修复 CI 编译错误",
            subtitle = "移除已废弃的 material3.ExperimentalLayoutApi 引用，消除编译期未解析引用错误。",
            bullets = listOf(
                "修复：DictionaryApp.kt 中 import androidx.compose.material3.ExperimentalLayoutApi 已被上游 Compose 库移除，改为仅使用 foundation.layout.ExperimentalLayoutApi。",
            ),
            tip = "本次变更仅影响构建流程，不影响 App 运行体验。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.FilterAlt,
            title = "优化词典列表筛选与读音体验",
            subtitle = "精简词典列表冗余标签，优化排序/筛选按钮布局，读音按钮支持点击切换与状态反馈。",
            bullets = listOf(
                "移除冗余标签：排序/筛选下拉按钮下方的课程标签快捷芯片已移除（下拉菜单已提供相同功能，不再重复）。",
                "箭头位置优化：排序和筛选下拉按钮的箭头图标固定在按钮右侧边缘，而非紧跟在文字后面。",
                "读音按钮切换：词详情页的英音/美音按钮点击后显示停止图标和「停止」文字，再次点击即可停止播放。",
            ),
            tip = "入口：词典标签页 → 搜索框下方排序/筛选按钮；词详情页 → 朗读按钮。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "背词与推荐页新增课程筛选",
            subtitle = "背词（Study）与推荐（Recommendation）页新增课程标签 + 雅思频率组双维度筛选，每页独立记忆并持久化，让学习更聚焦。",
            bullets = listOf(
                "课程标签筛选：背词和推荐页顶部新增下拉菜单，可按「高中 3500 词」「高中短语」等课程标签过滤，仅学习选定词表的单词。",
                "频率组筛选：同时支持按雅思频率组（1–7 组）筛选，与课程标签自由组合，精确定位单词难度层级。",
                "每页独立记忆：背词页与推荐页的筛选设置分别持久化，互不干扰，默认均为「全部词表 + 全部组」。",
                "数据层适配：所有推荐抽样（核心新词 / 派生拓展 / 高频过渡）和背词冷启动梯度均感知当前筛选范围，不会漏出范围外的词。",
            ),
            tip = "可在背词或推荐页顶部找到筛选下拉菜单，选择词表或频率组后即刻生效。",
        ),
    )
}
