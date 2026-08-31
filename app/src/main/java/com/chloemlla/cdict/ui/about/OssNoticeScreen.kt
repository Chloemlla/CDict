package com.chloemlla.cdict.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.net.DonationClient
import com.chloemlla.cdict.core.net.DonationOutcome
import com.chloemlla.cdict.ui.ResponsiveContentBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssNoticeScreen(forced: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var supporters by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(forced) {
        // 首启强制阅读这一页时不联网；用户自己打开才顺带取一次鸣谢名单，取不到就不显示这一节。
        if (forced) return@LaunchedEffect
        val outcome = DonationClient().fetchChannels()
        if (outcome is DonationOutcome.Success) supporters = outcome.info.supporters
    }
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
                if (supporters.isNotEmpty()) {
                    item(key = "supporters") {
                        SectionCard(icon = Icons.Filled.Celebration, title = "赞赏鸣谢名单") {
                            Text(
                                "以下朋友赞赏并同意署名。名单由服务端实时下发，核实转账备注后即时更新，" +
                                    "不需要更新应用；赞赏完全自愿，也不解锁任何功能。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            SupporterNameFlow(supporters)
                        }
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
