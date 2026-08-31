package com.chloemlla.cdict.ui.about

object AboutData {
    const val appName = "CDict"
    const val appDescription = "离线优先的雅思词典 Android 应用"
    const val sourceUrl = "https://github.com/Chloemlla/CDict"
    const val clashPartnerReleaseUrl =
        "https://github.com/Chloemlla/ClashMetaForAndroid/releases/latest"
    const val projectLicense = "GNU Affero General Public License v3.0 (AGPL-3.0)"
    const val projectLicenseUrl = "https://github.com/Chloemlla/CDict/blob/main/LICENSE"
    const val freeNoticeTitle = "永久免费 · 谨防上当受骗"
    const val freeNoticeBody = "本项目为兴趣驱动的开源雅思词典应用，永久免费。不会以“正版激活、付费解锁、私下转账”等名义收费。请仅通过官方源码仓库与可信发行渠道获取；任何声称“收费版 / 内部版 / 破解授权”的都可能是骗局，请勿上当。"
    const val disclaimer = "词典核心完全离线可用；内置的在线语音合成与翻译网关为公开资料收集的接口，仅用于可选的在线翻译与发音，可能随时失效或变更。请在遵守当地法律法规的前提下使用，并支持正版。"

    data class LegalDoc(val title: String, val url: String)

    val legalDocs: List<LegalDoc> = listOf(
        LegalDoc(
            title = "用户协议",
            url = "https://github.com/Chloemlla/CDict/blob/main/docs/legal/user-agreement.md",
        ),
        LegalDoc(
            title = "隐私政策",
            url = "https://github.com/Chloemlla/CDict/blob/main/docs/legal/privacy-policy.md",
        ),
        LegalDoc(
            title = "开源协议",
            url = "https://github.com/Chloemlla/CDict/blob/main/LICENSE",
        ),
    )

    data class AppPermission(val name: String, val purpose: String, val scope: String?)

    val appPermissions: List<AppPermission> = listOf(
        AppPermission(
            name = "网络",
            purpose = "连接互联网，用于可选的在线翻译与发音（在线合成 / 有道）。",
            scope = "INTERNET",
        ),
        AppPermission(
            name = "网络状态",
            purpose = "判断当前有没有可用网络，联网功能失败时给出对应提示，不读取具体网络标识。",
            scope = "ACCESS_NETWORK_STATE",
        ),
        AppPermission(
            name = "安装应用",
            purpose = "在你确认更新后，安装应用内下载的新版本安装包；不会在后台自动安装任何东西。",
            scope = "REQUEST_INSTALL_PACKAGES",
        ),
        AppPermission(
            name = "存储",
            purpose = "把赞赏页的收款码图片保存到相册；仅 Android 9 及以下需要此权限，且只在你点击保存时请求，不读取相册里的其他文件。",
            scope = "WRITE_EXTERNAL_STORAGE",
        ),
    )

    data class OssCredit(
        val name: String,
        val author: String,
        val description: String,
        val license: String,
        val url: String?,
    )

    val credits: List<OssCredit> = listOf(
        OssCredit(
            name = "Kotlin",
            author = "JetBrains",
            description = "应用使用的编程语言。",
            license = "Apache-2.0",
            url = "https://kotlinlang.org",
        ),
        OssCredit(
            name = "Jetpack Compose",
            author = "Android Open Source Project",
            description = "声明式 UI 框架。",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/compose",
        ),
        OssCredit(
            name = "Material 3 (Compose)",
            author = "Android Open Source Project",
            description = "Material Design 3 组件与主题。",
            license = "Apache-2.0",
            url = "https://m3.material.io",
        ),
        OssCredit(
            name = "AndroidX (Activity / Lifecycle / Core-ktx)",
            author = "Android Open Source Project",
            description = "Android 基础库与生命周期。",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack",
        ),
        OssCredit(
            name = "Room",
            author = "Android Open Source Project",
            description = "本地 SQLite ORM（词典 / 背词 / 翻译缓存三库）。",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/room",
        ),
        OssCredit(
            name = "SQLite / FTS5",
            author = "SQLite Consortium",
            description = "内嵌数据库与全文检索（词典搜索）。",
            license = "Public Domain",
            url = "https://sqlite.org",
        ),
        OssCredit(
            name = "kotlinx-coroutines",
            author = "JetBrains",
            description = "协程异步。",
            license = "Apache-2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        OssCredit(
            name = "Lumen Crash SDK",
            author = "Chloemlla / Project Lumen",
            description = "Android 崩溃采集桥（版本构建时自动解析）。",
            license = "见 Project-Lumen 仓库声明",
            url = "https://github.com/Chloemlla/Project-Lumen",
        ),
        OssCredit(
            name = "词典数据 / FLDC / ISDC",
            author = "授权导出",
            description = "内置词库的数据来源：FLDC 参考数据、ISDC 导出与授权 distribution 富内容合并。",
            license = "数据来源声明",
            url = "https://fldc.pages.dev",
        ),
        OssCredit(
            name = "在线语音合成 / 翻译网关",
            author = "第三方公开接口",
            description = "可选的在线翻译与发音（逆向公开接口，客户端常量，可随时失效）。",
            license = "私有接口",
            url = null,
        ),
    )
}
