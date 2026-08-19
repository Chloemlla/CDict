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
            title = "全面优化交互体验",
            subtitle = "翻译/背词/推荐/词典四页统一升级：翻译方向一键互换、背词答对庆祝动画、推荐图例可筛选、进度条平滑动画、空态插画化。",
            bullets = listOf(
                "翻译页：翻译方向选择器新增 ⇄ 互换按钮，中英/英中一键切换；空态新增脉冲插画与技巧提示。",
                "背词页：答对题目新增检查图标脉冲庆祝动画；进度条新增平滑动画与达成提示。",
                "推荐页：5:3:2 配比图例变为可点击筛选器，支持单类别聚焦；进度条同样新增平滑动画与达成提示。",
                "词典页：搜索框尾部加载指示器更醒目，空态搜索建议交互更流畅。",
                "通用：按压缩放反馈覆盖更多交互元素，无障碍标签补全，视觉层级更清晰。",
            ),
            tip = "入口：翻译方向选择器、背词复习反馈、推荐页顶部图例、词典搜索框、各页空态。",
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
    )
}