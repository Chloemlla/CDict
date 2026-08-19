package com.chloemlla.cdict.ui.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
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
            title = "全应用交互与无障碍优化",
            subtitle = "围绕阅读、操作反馈、状态表达和大屏适配，系统优化词典、背词、翻译、推荐及关于页体验。",
            bullets = listOf(
                "词典与翻译：搜索、输入和结果状态更清晰；长文本自动截断，译文可一键复制，软键盘不再遮挡内容。",
                "背词与推荐：进度、选中、正确/错误和筛选状态更直观；操作提供触觉确认，关键动作保持真实禁用或二次确认。",
                "无障碍与大屏：补齐标题、页面、进度及控件语义；主操作满足触控尺寸，宽屏内容保持舒适阅读宽度。",
                "关于与更新：浮层关闭规则更明确，更新下载显示真实进度、文件信息与安装提示。",
            ),
            tip = "入口：词典、背词、翻译、推荐及关于页均有可见改进。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "交互体验打磨与缺陷修复",
            subtitle = "修复推荐页类别筛选对当前卡片状态的副作用，优化答对动效为平滑单次回弹与光环扩散，完善方向互换与无障碍体验。",
            bullets = listOf(
                "推荐页：类别筛选（核心/派生/过渡）严格作用于「接下来」预览队列，当前卡片始终保持真实队首，防止按钮操作词与卡面展示脱节；图例支持均分宽度与复选语义。",
                "背词页：答对动效重构为 420ms 单次回弹与扩散光环，避免循环脉冲在短暂题目切换期内产生抖动；修复进度条颜色恒等表达式与冗余修饰符。",
                "翻译页：direction.canSwap 属性精确控制互换按钮显隐，移除非 auto 方向硬编码检查；清理已弃用的 StateCard 遗留代码与未引用导入。",
                "代码健康：补齐 TranslationModels 穷举映射，清理无用语义与图标导入，静态花括号与圆括号校验全绿。",
            ),
            tip = "入口：推荐页顶部图例、背词复习答对动效、翻译方向互换按钮。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.NewReleases,
            title = "全面优化加载/错误/空态交互",
            subtitle = "词典、背词、推荐、翻译四页统一升级：脉冲骨架屏加载、可重试错误卡片、可操作空状态与搜索建议，操作更顺滑。",
            bullets = listOf(
                "统一加载骨架屏：四页加载态均改为 3 行脉冲骨架动画，替代空白转圈，视觉连贯。",
                "可重试错误卡片：错误状态新增重试按钮，点击即可重新发起请求，无需下拉刷新。",
                "可操作空状态：背词页空态新增范围筛选与「查看全部/刷新」；推荐页空态新增「刷新/再来一批」双动作与技巧提示；翻译页空态新增使用技巧卡片；词典页空态新增「您是否想找」纠错与课程标签重置。",
                "搜索加载指示器：词典页搜索框尾部图标在请求期间显示旋转进度，清晰告知正在加载。",
                "按压缩放反馈：背词选项卡、推荐页即将到来行、主要/次要按钮均新增 0.97-0.98 倍按压缩放动画，触感更真实。",
                "无障碍增强：交互元素统一补充 contentDescription、Role.Button 与 stateDescription，屏幕阅读器友好。",
            ),
            tip = "入口：词典搜索/列表；背词队列/空态；推荐卡片/空态；翻译输入/结果。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.Update,
            title = "发布自动化加固",
            subtitle = "正式版本发布改为幂等且稳定的脚本流程，重复发布同一提交时自动覆盖资产而不是失败，并保持最新版本标记正确。",
            bullets = listOf(
                "幂等发布：同一次提交重复发布时跳过重复建 tag，直接覆盖更新发布资产，不再因 tag 已存在而失败，并正确标记最新版本。",
                "正式发布只发生在 main 分支推送或显式触发时，临时分支的构建不会再误生成公开正式版本。",
                "发布认证：使用仓库自动生成的令牌完成版本创建与资产上传，无需额外配置密钥。",
            ),
            tip = "发布行为：GitHub Actions → build → release job。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.Update,
            title = "应用内更新检测",
            subtitle = "新增 GitHub Release 更新检测，启动时自动检查新版本并支持应用内下载安装。",
            bullets = listOf(
                "自动检查：应用启动后自动请求 GitHub Releases API，发现新版本时弹窗提示。",
                "手动检查：关于页新增「检查更新」入口，可随时手动触发版本检查。",
                "应用内下载安装：匹配设备 ABI 选择最优 APK，下载时显示进度，校验 SHA256 后引导安装。",
                "安全校验：下载完成后验证 APK 的 SHA256 指纹，确保与发布资产一致。",
                "网络权限：补充网络状态读取权限，保证检查更新在系统上稳定运行。",
            ),
            tip = "入口：关于页 → 检查更新；或启动时自动检测。",
        ),
    )
}