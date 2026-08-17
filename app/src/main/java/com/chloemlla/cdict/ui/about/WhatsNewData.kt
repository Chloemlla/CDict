package com.chloemlla.cdict.ui.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
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
        WhatsNewSlide(
            icon = Icons.AutoMirrored.Filled.MenuBook,
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
            subtitle = "构建工具链由 Java 17 升级到 Java 21，并迁移到 AGP built-in Kotlin + KSP。",
            bullets = listOf(
                "Java 17 → Java 21。",
                "独立 Kotlin 插件 + kapt 迁移到 AGP built-in Kotlin + KSP（Room），构建告警清零。",
                "Android Lint 零告警：按报告升级 core-ktx / 协程 / Robolectric，SharedPreferences 改用 KTX。",
                "AAB 资源压缩与 lifecycle 版本为刻意设计，已登记 lint 白名单。",
                "MetadataEntity 与词典库 schema 对齐，DAO 元数据查询可编译。",
            ),
            tip = "对日常使用无感；属于工程加固。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.BugReport,
            title = "提供 .debug 包名调试构建",
            subtitle = "工作流新增 debug 任务，并行产出并上传调试版 APK：包名带 .debug 后缀，可与正式应用同时安装。",
            bullets = listOf(
                "调试版 applicationId 为 com.chloemlla.cdict.debug。",
                "CI 的 debug 任务与 verify 并行构建，产物作为 cdict-debug 工件上传。",
                "调试版用正式签名密钥签名，可侧载安装/覆盖，便于对照验证。",
            ),
            tip = "入口：GitHub Actions 工件 cdict-debug / com.chloemlla.cdict.debug。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.Info,
            title = "朗读回退原因可查",
            subtitle = "当朗读落到系统 TTS 时，可查明 vivo / 有道两级为何失败：应用内显示，也能经 adb 拉日志。",
            bullets = listOf(
                "「关于 → 朗读诊断」直接显示最近一次回退原因，如 vivo 拒绝 errorCode、HTTP 状态、网络异常、空响应等。",
                "adb 日志：adb logcat -s CDictAudio:I 可见同一原因。",
                "只记录最终落到系统 TTS 的那一次；日志带文本与音色。",
            ),
            tip = "入口：关于 → 朗读诊断，或 adb logcat -s CDictAudio:I。",
        ),
        WhatsNewSlide(
            icon = Icons.Filled.CheckCircle,
            title = "修复 vivo TTS 请求被拒，朗读不再落到系统 TTS",
            subtitle = "vivo TTS 请求体此前手工拼接既漏引号又带前导零，被服务端连续以 JSON parse HTTP 400 拒绝，朗读被迫退回系统 TTS；现改用 org.json 统一构建合法 JSON 并去除前导零，恢复 vivo 音色。",
            bullets = listOf(
                "前导零：deviceid=00000000000000 被 vivo 当数值反序列化，Jackson 拒前导零（400 Leading zeroes not allowed）；改为无前导零的 0。",
                "拼接漏引号：多个字段未加引号，去掉前导零后服务器继续解析即撞上未引号字段再次 400；改用 org.json 构建，保证 JSON 合法。",
                "若仍被拒，「朗读诊断 / adb logcat -s CDictAudio:I」会显示下一步的具体原因。",
            ),
            tip = "验证：词详情页点喇叭，发音不再退回系统 TTS。",
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
