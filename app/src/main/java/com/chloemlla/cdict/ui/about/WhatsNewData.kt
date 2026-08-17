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
            title = "修复词典解压的 Brotli 编译错误",
            subtitle = "数据库解压改用纯 Java 库实现，避免编译环境缺失原生 Brotli 类导致的构建失败。",
            bullets = listOf(
                "修复：CodeQL / 构建 / 单元测试均因 android.util.BrotliInputStream 在编译环境中不可用而失败，现已改用纯 Java 的 org.brotli:dec 库。",
                "影响：解压为一次性操作（约 0.5–2 秒），纯 Java 实现的性能差异可忽略。",
                "后续：编译环境就绪后可恢复原生 Brotli 解码器以获得 2-3 倍加速。",
            ),
            tip = "本次修复仅影响构建流程，不影响 App 使用体验。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "新增高中英语短语库（1775 条）",
            subtitle = "词典离线数据库新增「高中英语短语手札」，覆盖 11 个分类共 1262 条短语，按课程标签过滤。",
            bullets = listOf(
                "数据：从 docx 源文件解析 1413 条短语，去重后入库 1262 条，按「形容词/副词短语」「动词+介词/副词」「名词短语」等 11 个分组。",
                "展示：短语库以独立分组显示在词典列表中，在筛选下拉菜单选择「高中短语」课程标签即可只看短语。",
                "与词库条目共存：已在主词库中的条目不会重复添加，新短语独立入库，不破坏原有词频分组。",
            ),
            tip = "入口：词典标签页 → 筛选下拉菜单 → 选择「高中短语」标签。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "APK 瘦身与兼容性优化",
            subtitle = "词典离线数据库使用 Brotli 极限压缩（从 90+ MB 降至约 8–10 MB），同时补充了多项兼容性保障。",
            bullets = listOf(
                "APK 瘦身：词典离线数据库在构建时使用 Brotli 极限压缩，APK 安装包体积显著减小。",
                "首次启动：App 在后台自动解压词典数据，完成后自动进入主界面，整个过程约需 0.5–2 秒。",
                "版本自动重建：App 更新后自动检测版本号变化，自动删除旧词典并重新解压，无需手动操作。",
                "存储空间检测：解压前检查手机剩余存储空间，空间不足时弹出友好提示，避免中途失败。",
                "语言资源精简：构建配置添加 resourceConfigurations，只保留中英文资源，进一步缩小 APK。",
                "后续启动：解压后的文件会缓存到本地，不会重复解压。",
            ),
            tip = "首次启动会看到「正在加载离线词典…」提示，等待片刻即可进入主界面。",
        ),
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
            icon = Icons.Filled.Bookmark,
            title = "词详情页标记「高中 3500 词」",
            subtitle = "词典发布流水线会把高中 3500 词表中与词库同时存在的词条打上课程标签，词详情页在词条卡片中直接展示。",
            bullets = listOf(
                "数据：merge 工作流的最后一步下载 3500.txt，按词头解析并归一化匹配，幂等写入 curriculumTags 列，可重复运行不重复打标。",
                "展示：词详情页「词条」卡片在翻译下方显示「高中 3500 词」标签，多个标签自动换行、长标签不裁剪。",
                "签名：标签写入后重新计算 assetSignature，发布库与校验、checksum 保持一致。",
            ),
            tip = "入口：任意词详情页 → 词条卡片。若该词在 3500 词表中即可看到标签。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.FilterAlt,
            title = "主页词典筛选改版：下拉菜单",
            subtitle = "词典列表的筛选不再是一排并排的按钮，改为两个独立下拉菜单：排序与课程标签。",
            bullets = listOf(
                "排序：按频率、按字母、字母倒序，在下拉菜单中切换。",
                "课程标签：选择「高中 3500 词」后列表只显示带该标签的词条；标签选项由发布词库自动列出，未来新增课程列表无需改版即可出现。",
                "筛选只在浏览列表时生效；输入搜索词后自动收起，清空搜索后恢复上次筛选。",
            ),
            tip = "入口：词典标签页 → 顶部搜索框下方两个下拉菜单。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.BugReport,
            title = "词典搜索与翻译的稳定性修复",
            subtitle = "本次构建修复了搜索崩溃与翻译结果错配，并补齐了词详情与宽屏推荐页的可用性。",
            bullets = listOf(
                "搜索：输入 ( ) \" : ^ 等字符不再导致搜索崩溃或结果错乱，异常时自动回退为子串匹配。",
                "翻译：修改原文或方向会立即清空旧结果，且旧请求不再覆盖新请求的翻译。",
                "词详情：加载失败不再停留在无限加载，而是显示错误并可一键重试。",
                "推荐页（平板 / 大屏）：宽屏布局补回每日目标、进度与 5:3:2 图例，当前卡片可滚动。",
                "词详情：课程标签支持自动换行，长标签不再被裁剪。",
            ),
            tip = "主要涉及词典、翻译、推荐三个标签页，可直接对照验证。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "「AI 补充」英文内容支持朗读与翻译",
            subtitle = "词详情页的「AI 补充」分区中，英文内容（词间关系、词形变化、词源）均可点按朗读并自动附中文翻译。",
            bullets = listOf(
                "词间关系：近义词 / 反义词 / 相关词逐条朗读与翻译；目标词在词库中时仍可点「前往」跳转。",
                "词形变化：每个变形词均可朗读与翻译，变形标签（如 participle、present）保留在行尾。",
                "词源：每条词源段落均可朗读并附中文翻译。",
            ),
            tip = "入口：词详情页 → 对应分区 → 点喇叭朗读，译文自动显示在下方。",
        ),
    )
}
