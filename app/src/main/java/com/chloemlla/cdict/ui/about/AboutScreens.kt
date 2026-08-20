package com.chloemlla.cdict.ui.about

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.chloemlla.cdict.R
import com.chloemlla.cdict.core.audio.PronunciationDiagnostics
import com.chloemlla.cdict.core.net.ClashPartner
import com.chloemlla.cdict.core.net.summary
import com.chloemlla.cdict.ui.ResponsiveContentBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    external: Boolean = false,
) {
    val interactive = onClick != null || onLongClick != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (interactive) {
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .combinedClickable(
                            onClick = onClick ?: {},
                            onClickLabel = if (external) "$title，将在浏览器中打开" else title,
                            role = if (onClick != null) Role.Button else null,
                            onLongClick = onLongClick,
                            onLongClickLabel = if (onLongClick != null) "长按复制" else null,
                        )
                } else {
                    Modifier
                }
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (external) {
                    Icons.AutoMirrored.Filled.OpenInNew
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = if (external) Modifier.size(18.dp) else Modifier,
            )
        }
    }
}

/** 整行可切换的开关项：点击行任意位置都会切换，开关本体不单独获得无障碍焦点。 */
@Composable
private fun AboutSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    stateText: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .semantics { stateDescription = stateText }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

/** 关于页分组小标题，作为无障碍 heading，方便快速跳转。 */
@Composable
private fun AboutSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

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
                        subtitle = "完全自愿；收款码由服务端实时下发，应用永久免费",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclarationsScreen(onBack: () -> Unit) {
    val controller = LocalAboutController.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用声明", modifier = Modifier.semantics { heading() }) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                AboutRow(
                    title = "法律信息",
                    subtitle = "用户协议、隐私政策、开源协议",
                    onClick = { controller.push(AboutScreenRoute.LegalInfo) },
                )
                HorizontalDivider()
                AboutRow(
                    title = "开源许可声明",
                    subtitle = "源码地址、永久免费提示、协议与依赖鸣谢",
                    onClick = { controller.push(AboutScreenRoute.OssNotice) },
                )
                HorizontalDivider()
                AboutRow(
                    title = "应用权限",
                    subtitle = "应用声明的系统权限及用途说明",
                    onClick = { controller.push(AboutScreenRoute.AppPermissions) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("法律信息", modifier = Modifier.semantics { heading() }) },
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    SelectionContainer {
                        Text(
                            "以下为应用提供的法律文件。点击条目会在浏览器中打开对应页面。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                items(AboutData.legalDocs) { doc ->
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = "${doc.title}，将在浏览器中打开",
                                role = Role.Button,
                            ) { UrlOpener.open(context, doc.url) }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            doc.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = titleColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
                Column(Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CreditCard(credit: AboutData.OssCredit, onClick: () -> Unit) {
    val cardShape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (credit.url != null) {
                    Modifier
                        .clip(cardShape)
                        .clickable(
                            onClickLabel = "${credit.name}，将在浏览器中打开",
                            role = Role.Button,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                }
            ),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        credit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        credit.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (credit.url != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                credit.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    credit.license,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssNoticeScreen(forced: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开源许可声明", modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    if (!forced) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .heightIn(min = 48.dp),
                ) {
                    Text(if (forced) "我已了解，继续" else "关闭")
                }
            }
        },
    ) { padding ->
        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "source") {
                    SectionCard(icon = Icons.Filled.Code, title = "官方开源地址") {
                        Text(
                            AboutData.sourceUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = { UrlOpener.open(context, AboutData.sourceUrl) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Text("打开仓库", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = { UrlOpener.copy(context, AboutData.sourceUrl, "已复制仓库链接") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Text("复制链接", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "「打开仓库」将在浏览器中打开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                item(key = "free") {
                    SectionCard(
                        icon = Icons.Filled.Info,
                        title = AboutData.freeNoticeTitle,
                        titleColor = MaterialTheme.colorScheme.error,
                    ) {
                        Text(AboutData.freeNoticeBody, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item(key = "license") {
                    SectionCard(icon = Icons.Filled.Gavel, title = "本项目开源协议") {
                        Text(AboutData.projectLicense, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "完整协议文本见仓库 LICENSE 文件。使用、修改与再分发须遵守 AGPL-3.0。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { UrlOpener.open(context, AboutData.projectLicenseUrl) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("查看 LICENSE（浏览器打开）", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            AboutData.disclaimer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "creditsHeading") {
                    SelectionContainer {
                        Column {
                            Text(
                                "第三方依赖鸣谢",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "以下为本应用使用到的第三方开源项目、数据来源与接口。点击条目会在浏览器中打开对应链接。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(AboutData.credits) { credit ->
                    CreditCard(
                        credit = credit,
                        onClick = {
                            credit.url?.let { url -> UrlOpener.open(context, url) }
                        },
                    )
                }
                item(key = "bottomSpacer") {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用权限", modifier = Modifier.semantics { heading() }) },
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    SelectionContainer {
                        Text(
                            "以下为应用声明使用的系统权限及其用途。权限仅在对应功能首次使用时请求，未授权不会影响其余功能。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                items(AboutData.appPermissions) { permission ->
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(16.dp))
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        permission.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    permission.scope?.let { scope ->
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            scope,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    permission.purpose,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhatsNewScreen(forced: Boolean, onBack: () -> Unit) {
    val slides = remember { WhatsNewData.slides() }
    val lastIndex = slides.lastIndex
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (forced) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                }
                Text(
                    "${currentPage + 1} / ${slides.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        stateDescription = "当前第 ${currentPage + 1} 页，共 ${slides.size} 页"
                    },
                )
                Spacer(Modifier.weight(1f))
                if (currentPage < lastIndex) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("跳过") }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics {
                        stateDescription = "当前第 ${currentPage + 1} 页，共 ${slides.size} 页，可左右滑动浏览"
                    },
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    if (page == 0) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.size(112.dp),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(112.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = slide.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    SelectionContainer {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                slide.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                slide.subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SelectionContainer {
                        Column(Modifier.fillMaxWidth()) {
                            slide.bullets.forEach { bullet ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        bullet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    slide.tip?.let { tip ->
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                SelectionContainer {
                                    Text(
                                        tip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.semantics {
                        stateDescription = "当前第 ${currentPage + 1} 页，共 ${slides.size} 页"
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    slides.indices.forEach { index ->
                        val selected = currentPage == index
                        val dotWidth by animateDpAsState(
                            targetValue = if (selected) 20.dp else 8.dp,
                            animationSpec = tween(200),
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                        },
                        enabled = currentPage > 0,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("上一页")
                    }
                    Button(
                        onClick = {
                            if (currentPage < lastIndex) {
                                scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(
                            when {
                                currentPage < lastIndex -> "下一步"
                                forced -> "知道了"
                                else -> "关闭"
                            },
                        )
                    }
                }
            }
        }
    }
}
