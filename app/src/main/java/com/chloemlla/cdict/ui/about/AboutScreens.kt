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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.chloemlla.cdict.R
import com.chloemlla.cdict.core.audio.PronunciationDiagnostics
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
                            onClickLabel = title,
                            role = if (onClick != null) Role.Button else null,
                            onLongClick = onLongClick,
                            onLongClickLabel = if (onLongClick != null) "长按复制" else null,
                        )
                        .semantics {
                            if (onClick != null || onLongClick != null) {
                                role = Role.Button
                            }
                        }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 4.dp, vertical = 14.dp)
            .heightIn(min = 48.dp),
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
                    maxLines = 2,
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
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
    val controller = LocalAboutController.current
    val commitUrl = "${AboutData.sourceUrl}/commit/${BuildInfo.commitHash}"
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
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                AboutRow(
                    title = "当前版本",
                    trailing = {
                        SelectionContainer {
                            Text(
                                BuildInfo.versionLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                HorizontalDivider()
                AboutRow(
                    title = "构建信息",
                    subtitle = "构建于 ${BuildInfo.formatBuildTime()} · 长按复制提交哈希",
                    trailing = {
                        Text(
                            BuildInfo.shortHash,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        if (!BuildInfo.isDevBuild) {
                            UrlOpener.open(context, commitUrl)
                        }
                    },
                    onLongClick = {
                        UrlOpener.copy(context, BuildInfo.commitHash, "已复制提交哈希")
                    },
                )
                HorizontalDivider()
                AboutRow(
                    title = "源码仓库",
                    subtitle = AboutData.sourceUrl,
                    onClick = { UrlOpener.open(context, AboutData.sourceUrl) },
                )
                HorizontalDivider()
                AboutRow(
                    title = "朗读优先来源",
                    subtitle = if (youdaoFirst) {
                        "有道优先，失败时使用 vivo TTS"
                    } else {
                        "vivo TTS 优先，失败时使用有道"
                    },
                    trailing = {
                        Switch(
                            checked = youdaoFirst,
                            onCheckedChange = { checked ->
                                youdaoFirst = checked
                                aboutStore.youdaoFirst = checked
                            },
                        )
                    },
                )
                HorizontalDivider()
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
                Button(
                    onClick = onCheckForUpdate,
                    enabled = updateCheckEnabled && !updateCheckInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
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
                Spacer(Modifier.height(4.dp))
                val pronunciationDiag by PronunciationDiagnostics.lastFallback.collectAsState()
                AboutRow(
                    title = "朗读诊断",
                    subtitle = when (val d = pronunciationDiag) {
                        null -> "暂无回退记录：先在词详情页点喇叭朗读一次"
                        else -> "vivo: ${d.vivoReason ?: "—"} · 有道: ${d.youdaoReason}"
                    },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                SelectionContainer {
                    Text(
                        "以下为应用提供的法律文件。点击条目在浏览器中打开对应页面。",
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
                        .clickable { UrlOpener.open(context, doc.url) }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .heightIn(min = 56.dp),
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
                        .clickable(onClick = onClick)
                        .semantics { role = Role.Button }
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
                shape = RoundedCornerShape(8.dp),
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
                        .navigationBarsPadding(),
                ) {
                    Text(if (forced) "我已了解，继续" else "关闭")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("打开仓库", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { UrlOpener.copy(context, AboutData.sourceUrl, "已复制仓库链接") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("复制链接", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
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
                    FilledTonalButton(onClick = { UrlOpener.open(context, AboutData.projectLicenseUrl) }) {
                        Text("查看 LICENSE")
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
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "以下为本应用使用到的第三方开源项目、数据来源与接口。点击条目可打开对应链接。",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                                Text(permission.name, style = MaterialTheme.typography.titleMedium)
                                permission.scope?.let { scope ->
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        scope,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline,
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
                    TextButton(onClick = onBack) { Text("跳过") }
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
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.weight(1f),
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
