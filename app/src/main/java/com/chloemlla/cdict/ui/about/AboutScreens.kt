package com.chloemlla.cdict.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.R
import com.chloemlla.cdict.core.audio.PronunciationDiagnostics
import com.chloemlla.cdict.core.net.ClashPartner
import com.chloemlla.cdict.core.net.summary
import com.chloemlla.cdict.ui.ResponsiveContentBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    updateCheckEnabled: Boolean,
    updateCheckInProgress: Boolean,
    onCheckForUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val aboutStore = remember { AboutStore(context) }
    var youdaoFirst by remember { mutableStateOf(aboutStore.youdaoFirst) }
    var autoCheckUpdate by remember { mutableStateOf(aboutStore.autoCheckUpdate) }
    var clashAdapt by remember { mutableStateOf(aboutStore.clashProxyAdapt) }
    val clashState by ClashPartner.state.collectAsState()
    // 进入关于页时重新拉一次伙伴状态：用户可能刚在 Clash 里启停内核。
    LaunchedEffect(Unit) { ClashPartner.refresh() }
    val openClashApp: (() -> Unit)? = clashState.installedPackage?.let { pkg ->
        ({ UrlOpener.openApp(context, pkg, "Clash Meta for Android") })
    }
    val controller = LocalAboutController.current
    val commitUrl = "${AboutData.sourceUrl}/commit/${BuildInfo.commitHash}"
    // 开发构建没有真实提交哈希，此时整行不可点击，避免打开无效链接。
    val openCommitPage: (() -> Unit)? = if (BuildInfo.isDevBuild) {
        null
    } else {
        ({ UrlOpener.open(context, commitUrl) })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(16.dp))
                SelectionContainer {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(AboutData.appName, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            AboutData.appDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    AboutSectionLabel("版本与构建")
                    AboutRow(
                        title = "当前版本",
                        trailing = {
                            SelectionContainer {
                                Text(
                                    BuildInfo.versionLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                    AboutRow(
                        title = "构建信息",
                        subtitle = if (BuildInfo.isDevBuild) {
                            "构建于 ${BuildInfo.formatBuildTime()} · 长按复制提交哈希"
                        } else {
                            "构建于 ${BuildInfo.formatBuildTime()} · 点击查看提交，长按复制哈希"
                        },
                        trailing = {
                            Text(
                                BuildInfo.shortHash,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = openCommitPage,
                        onLongClick = {
                            UrlOpener.copy(context, BuildInfo.commitHash, "已复制提交哈希")
                        },
                        external = !BuildInfo.isDevBuild,
                    )
                    HorizontalDivider()
                    AboutRow(
                        title = "源码仓库",
                        subtitle = AboutData.sourceUrl,
                        onClick = { UrlOpener.open(context, AboutData.sourceUrl) },
                        external = true,
                    )

                    AboutSectionLabel("软件更新")
                    AboutSwitchRow(
                        title = "自动检查软件更新",
                        subtitle = when {
                            !updateCheckEnabled -> "当前版本不支持检查更新"
                            autoCheckUpdate -> "启动应用时自动检查新版本，发现更新才提示"
                            else -> "已关闭，仅在手动点击「检查更新」时检查"
                        },
                        checked = autoCheckUpdate,
                        stateText = if (autoCheckUpdate) "自动检查更新已开启" else "自动检查更新已关闭",
                        enabled = updateCheckEnabled,
                        onCheckedChange = { checked ->
                            autoCheckUpdate = checked
                            aboutStore.autoCheckUpdate = checked
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onCheckForUpdate,
                        enabled = updateCheckEnabled && !updateCheckInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics {
                                stateDescription = when {
                                    !updateCheckEnabled -> "当前版本不支持检查更新"
                                    updateCheckInProgress -> "正在检查更新"
                                    else -> "可检查更新"
                                }
                            },
                    ) {
                        Icon(Icons.Filled.Update, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (updateCheckInProgress) "检查中…" else "检查更新")
                    }

                    AboutSectionLabel("朗读")
                    AboutSwitchRow(
                        title = "朗读优先来源",
                        subtitle = if (youdaoFirst) {
                            "有道优先，失败时使用在线合成引擎"
                        } else {
                            "在线合成引擎优先，失败时使用有道"
                        },
                        checked = youdaoFirst,
                        stateText = if (youdaoFirst) "有道优先" else "在线合成优先",
                        onCheckedChange = { checked ->
                            youdaoFirst = checked
                            aboutStore.youdaoFirst = checked
                        },
                    )
                    HorizontalDivider()
                    val pronunciationDiag by PronunciationDiagnostics.lastFallback.collectAsState()
                    AboutRow(
                        title = "朗读诊断",
                        subtitle = when (val d = pronunciationDiag) {
                            null -> "暂无回退记录：先在词详情页点喇叭朗读一次"
                            else -> "在线合成: ${d.vivoReason ?: "—"} · 有道: ${d.youdaoReason}"
                        },
                    )

                    AboutSectionLabel("伙伴应用")
                    AboutSwitchRow(
                        title = "跟随 Clash 代理",
                        subtitle = if (clashAdapt) {
                            "Clash 内核在跑且未开隧道时，在线翻译 / 朗读 / 更新检查改走其本地代理"
                        } else {
                            "已关闭，联网请求始终直连"
                        },
                        checked = clashAdapt,
                        stateText = if (clashAdapt) "跟随 Clash 代理已开启" else "跟随 Clash 代理已关闭",
                        onCheckedChange = { checked ->
                            clashAdapt = checked
                            aboutStore.clashProxyAdapt = checked
                            ClashPartner.setAdaptEnabled(checked)
                        },
                    )
                    HorizontalDivider()
                    AboutRow(
                        title = "Clash Meta for Android",
                        subtitle = clashState.summary(),
                        onClick = openClashApp,
                    )
                    HorizontalDivider()
                    AboutRow(
                        title = if (clashState.installedPackage == null) {
                            "下载 Clash Meta for Android（可选）"
                        } else {
                            "更新 Clash Meta for Android"
                        },
                        subtitle = if (clashState.installedPackage == null) {
                            "可选安装：装上并运行后，在线翻译 / 朗读 / 更新检查可跟随其代理；点击前往 GitHub Releases"
                        } else {
                            "点击前往 GitHub Releases 查看最新版；长按复制下载链接"
                        },
                        onClick = { UrlOpener.open(context, AboutData.clashPartnerReleaseUrl) },
                        onLongClick = {
                            UrlOpener.copy(
                                context,
                                AboutData.clashPartnerReleaseUrl,
                                "已复制伙伴应用下载链接",
                            )
                        },
                        external = true,
                    )

                    AboutSectionLabel("更多信息")
                    AboutRow(
                        title = "应用声明",
                        subtitle = "法律信息、开源许可声明、应用权限",
                        onClick = { controller.push(AboutScreenRoute.Declarations) },
                    )
                    HorizontalDivider()
                    AboutRow(
                        title = "本次更新说明",
                        subtitle = "基于 Commit Hash / Build Time 的本构建有意变更",
                        onClick = { controller.push(AboutScreenRoute.WhatsNew) },
                    )
                    HorizontalDivider()
                    AboutRow(
                        title = "赞赏支持",
                        subtitle = "完全自愿；收款码与鸣谢名单由服务端实时下发，应用永久免费",
                        onClick = { controller.push(AboutScreenRoute.Donation) },
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "开源协议见「应用声明 → 开源许可声明」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
