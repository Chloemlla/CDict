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
            title = "优化词典筛选按钮箭头位置",
            subtitle = "词典列表顶部的排序和筛选下拉按钮，其下拉箭头图标移至按钮最右侧，与常见设计对齐。",
            bullets = listOf(
                "「按频率」「全部词条」等下拉按钮的箭头图标现在固定在按钮右侧边缘，而非紧跟在文字后面。",
                "按钮文字较长时不再与箭头混在一起，视觉上更清晰。",
            ),
            tip = "入口：词典标签页 → 搜索框下方排序/筛选按钮。",
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
        WhatsNewSlide(
            icon = Icons.Filled.BugReport,
            title = "升级 CodeQL Action 到 v4",
            subtitle = "CodeQL Action v3 将在 2026 年 12 月被弃用，GitHub Actions 运行环境已升级到 Node 24。",
            bullets = listOf(
                "修复：CodeQL 分析步骤因 GitHub 服务器临时不可用而失败，属于基础设施问题，非代码缺陷。",
                "升级：codeql-action/init 和 codeql-action/analyze 从 @v3 升级到 @v4，兼容即将弃用的 v3 版本。",
                "后续：建议定期检查 GitHub Actions 依赖版本，避免因上游弃用导致 CI 中断。",
            ),
            tip = "本次变更仅影响 CI 工作流，不影响 App 运行。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.BugReport,
            title = "修复首次启动词典解压",
            subtitle = "首次启动时使用目标目录的实际可用空间进行检查，避免测试环境误判空间不足而跳过词典解压。",
            bullets = listOf(
                "修复：数据库目录尚不存在或系统 StatFs 不可用时，不再错误返回无可用空间。",
                "稳定：首次启动的并发解压仍由互斥锁串行执行，并继续校验数据库有效性。",
                "验证：预打包词典 schema 回归测试会确认解压成功且词条数量大于零。",
            ),
            tip = "本次修复不改变已有安装的数据库内容。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "构建直接使用预压缩词典",
            subtitle = "词典合并工作流预先生成 Brotli 资产，Android 构建不再每次重复压缩 90 MB 以上的数据库。",
            bullets = listOf(
                "合并：merge-phrases 和 merge-distribution 在发布词典时生成 dict.db.br，并同步更新 SHA-256 校验文件。",
                "构建：verify、debug、release 三个任务直接下载并校验 dict.db.br，省去重复压缩步骤。",
                "兼容：未压缩的 CDict-dict.db 仍保留在 Release 中，供后续增量合并使用。",
            ),
            tip = "本次优化不改变 App 首次启动时的解压逻辑。",
        ),
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
    )
}
