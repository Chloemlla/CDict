package com.chloemlla.cdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 一段英文文字的「朗读 + 自动翻译」展示。词典词条与背词卡片共用：
 * 顶部是原文与朗读按钮，下方据 [ui] 展示加载中 / 译文 / 失败重试；已内联
 * 中文（[pinnedZh]）时不再请求网络翻译。
 */
@Composable
internal fun SpeakableEnglishText(
    en: String,
    pinnedZh: String?,
    ui: PhraseUiState?,
    onTranslate: (String) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(en) {
        if (pinnedZh == null && ui == null) onTranslate(en)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = en,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onSpeak(en) },
                modifier = Modifier.semantics { contentDescription = "朗读 $en" },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            !pinnedZh.isNullOrBlank() -> Text(
                text = pinnedZh,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui is PhraseUiState.Loading -> Text(
                text = "翻译中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            ui is PhraseUiState.Done -> Text(
                text = ui.zh,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui is PhraseUiState.Error -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "翻译失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                TextButton(onClick = { onTranslate(en) }) { Text("重试") }
            }
        }
    }
}