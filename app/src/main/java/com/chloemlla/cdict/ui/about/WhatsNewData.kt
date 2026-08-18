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
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "高中短语获得 AI 语感标注",
            subtitle = "「高中短语」全书 579 条已完成 AI 语感标注，覆盖感情色彩、语体、精细语意、避坑与常用搭配。",
            bullets = listOf(
                "内容：579 条高中短语全部补齐语感字段（感情色彩 / 语体 / 精细语意 / 避坑 / 常用搭配），与主词库同等渲染。",
                "工具：annotate_dictionary.js 新增 --tag 课程标签过滤，命令为 node annotate_dictionary.js dict.db --tag 高中短语，按标签精确补标，不影响主词库。",
                "数据：已标注词典库经 Git LFS 管理并随版本 + Release 更新，短语语感随 App 下载的词典资产生效。",
            ),
            tip = "在词典标签页筛选「高中短语」，词条卡片即可看到语感与搭配信息。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "1.1.0 版本发布",
            subtitle = "本轮迭代新增高中英语短语库与课程标签筛选,并完成词典首次启动与打包体积的多项优化。",
            bullets = listOf(
                "短语库：内置「高中英语短语手札」,覆盖 11 个分类共约 1,262 条短语,可在筛选下拉菜单按「高中短语」查看。",
                "课程标签：词典与词详情页支持「高中 3500 词 / 高中短语」标签,按课程筛选词表。",
                "体积：词典在构建时经 Brotli 极限压缩,APK 体积显著减小;首次启动解压前检查可用空间。",
                "稳定：首次启动解压串行化并校验结果,翻译请求取消过期结果避免覆盖,崩溃 SDK 启动更可靠。",
            ),
            tip = "可在词典标签页的排序 / 筛选下拉菜单中选择课程标签。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.FilterAlt,
            title = "优化词典筛选体验",
            subtitle = "词典列表新增课程标签快捷筛选，并在词条卡片直接展示所属手札分组（如「高中短语」）。",
            bullets = listOf(
                "快捷筛选：浏览列表上方新增一组可横向滚动的标签芯片（如「高中短语」），一键即可只看对应分组，再次点击取消筛选。",
                "分组可见：词条卡片底部直接显示该词所属的课程标签，浏览时一眼即可识别词条来自哪个手札分组。",
                "筛选体验：原有的「全部词条／课程标签」下拉与快捷芯片联动，选中的标签在两种入口中同步高亮。",
            ),
            tip = "入口：词典标签页 → 搜索框下方标签栏（清除搜索词后可滚动查看）。",
        ),
    )
}
