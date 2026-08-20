package com.chloemlla.cdict.quicktranslate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.data.QuickWord
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.ui.about.UrlOpener
import kotlinx.coroutines.launch

/**
 * 系统文本选择工具条唤起的底部翻译弹窗。
 *
 * 只呈现结果，不承载编辑：原文与译文可长按选中，词库命中时才出现「前往 CDict」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTranslateSheet(
    state: QuickTranslateState,
    onRetry: () -> Unit,
    onToggleDirection: () -> Unit,
    onOpenEntry: (QuickWord) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val animatedDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(direction = state.direction, onClose = animatedDismiss)
            SourceBlock(source = state.source)
            TranslationBlock(state = state, onRetry = onRetry, onToggleDirection = onToggleDirection)
            state.entry?.let { entry ->
                EntryBlock(entry = entry, onOpenEntry = onOpenEntry)
            }
        }
    }
}

@Composable
private fun SheetHeader(direction: TranslationDirection, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "快速翻译",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "vivo 翻译引擎 · ${direction.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.semantics { contentDescription = "关闭快速翻译" },
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
        }
    }
}

@Composable
private fun SourceBlock(source: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "原文",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        // 超长选区不撑满整屏：限高后内部滚动，弹窗高度保持可预期。
        SelectionContainer(
            modifier = Modifier
                .heightIn(max = 140.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = source,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TranslationBlock(
    state: QuickTranslateState,
    onRetry: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "译文",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            val target = if (state.direction == TranslationDirection.AUTO_TO_ZH) "英文" else "中文"
            AssistChip(
                onClick = onToggleDirection,
                label = { Text("译为$target") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.semantics {
                    contentDescription = "改为译为$target，当前为${state.direction.label}"
                },
            )
        }
        when {
            state.translating -> TranslatingRow()
            state.error != null -> ErrorRow(message = state.error, onRetry = onRetry)
            else -> ResultRow(translation = state.translation, phonetic = state.phonetic)
        }
    }
}

@Composable
private fun TranslatingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = "正在翻译…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "翻译失败，请检查网络后重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SelectionContainer {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("重试")
        }
    }
}

@Composable
private fun ResultRow(translation: String?, phonetic: String?) {
    val context = LocalContext.current
    if (translation.isNullOrBlank()) {
        Text(
            text = "暂无译文",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionContainer(
            modifier = Modifier
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = translation,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        phonetic?.takeIf(String::isNotBlank)?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { UrlOpener.copy(context, translation, "已复制译文") },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("复制译文")
        }
    }
}

@Composable
private fun EntryBlock(entry: QuickWord, onOpenEntry: (QuickWord) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "词库已收录该词条",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = entry.word,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        entry.phonetic?.let { value ->
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    (entry.translation ?: entry.definition)?.let { gloss ->
                        Text(
                            text = gloss,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = { onOpenEntry(entry) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "前往 CDict 查看 ${entry.word} 的词条详情" },
            ) {
                Text("前往 CDict 查看详情")
            }
        }
    }
}
