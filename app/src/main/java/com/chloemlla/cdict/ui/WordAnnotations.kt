package com.chloemlla.cdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.data.WordEntity

/** 情感标注使用语义化主题色，确保深浅色模式下都有足够对比度。 */
@Composable
private fun emotionColors(value: String?): Pair<Color, Color>? = when (value?.trim()?.lowercase()) {
    "positive" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    "negative" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    "neutral" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    "context_dependent" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    else -> null
}

/** emotion_color 枚举值对应的中文标签；缺失或无法识别时返回 null。 */
fun emotionColorLabel(value: String?): String? = when (value?.trim()?.lowercase()) {
    "positive" -> "褒义"
    "negative" -> "贬义"
    "neutral" -> "中性"
    "context_dependent" -> "视语境"
    else -> null
}

/** register 枚举值对应的中文标签；无法识别时保留原始值。 */
fun registerLabel(value: String?): String? = value?.trim()?.lowercase()?.let {
    when (it) {
        "academic" -> "学术"
        "spoken" -> "口语"
        "written" -> "书面"
        "literary" -> "文学"
        "informal" -> "非正式"
        "neutral" -> "中性"
        else -> value.trim()
    }
}

/** collocations 按“；”连接；同时兼容常见的逗号分隔形式。 */
fun parseCollocations(value: String?): List<String> =
    value?.split("；", ";", "、", ",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

/** 解析逗号分隔的 aiSupplemented 字段，返回由补充分发流程写入的字段集合。 */
fun supplementedFields(word: WordEntity): Set<String> =
    word.aiSupplemented?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

/** 词条存在由补充分发流程写入的展示字段时返回 true。 */
fun wordHasAiSupplement(word: WordEntity): Boolean = supplementedFields(word).isNotEmpty()

/** 指定字段由补充分发流程写入时返回 true。 */
fun fieldSupplemented(word: WordEntity, field: String): Boolean = field in supplementedFields(word)

/** 任一标注字段有可展示内容时返回 true。 */
fun wordHasAnnotations(word: WordEntity): Boolean =
    word.emotionColor?.isNotBlank() == true ||
        word.register?.isNotBlank() == true ||
        word.nuanceDescription?.isNotBlank() == true ||
        word.usageWarning?.isNotBlank() == true ||
        parseCollocations(word.collocations).isNotEmpty()

@Composable
private fun AnnotationPill(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/** curriculumTags 是词典流程写入的分隔标签列表。 */
fun parseCurriculumTags(value: String?): List<String> =
    value?.split("；", ";", ",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

@Composable
fun CurriculumTagPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/** “AI 补充”标签标识由补充分发流程写入的内容。 */
@Composable
fun AiSupplementPill(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = "AI 补充",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/** 在段落末尾显示“AI 补充”标签；[show] 为 false 时不渲染。 */
@Composable
fun AiSupplementTrailing(show: Boolean, modifier: Modifier = Modifier) {
    if (show) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            AiSupplementPill()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordAnnotationBadges(word: WordEntity, modifier: Modifier = Modifier) {
    val emotion = emotionColors(word.emotionColor)
    val emotionLabel = emotionColorLabel(word.emotionColor)
    val register = registerLabel(word.register)
    if (emotion == null && register == null) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (emotion != null && emotionLabel != null) {
            // 只有颜色区分不足以说明标签含义，读屏时补上「情感色彩」维度。
            AnnotationPill(
                text = emotionLabel,
                bg = emotion.first,
                fg = emotion.second,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "情感色彩：$emotionLabel"
                },
            )
        }
        register?.let { registerText ->
            // 语域改用中性容器色：避免与「中性」情感标签渲染成完全相同的胶囊。
            AnnotationPill(
                text = registerText,
                bg = MaterialTheme.colorScheme.surfaceContainerHighest,
                fg = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "语域：$registerText"
                },
            )
        }
    }
}

/** 完整的 AI 标注区：标签、语感、搭配与高亮用法提醒。
 * 提供 [onTranslate] 和 [onSpeak] 时，搭配项可朗读并自动翻译；否则显示为静态标签。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordAnnotationSection(
    word: WordEntity,
    modifier: Modifier = Modifier,
    phraseStates: Map<String, PhraseUiState> = emptyMap(),
    onTranslate: ((String) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
    speakingKey: String? = null,
) {
    val nuance = word.nuanceDescription?.takeIf { it.isNotBlank() }
    val warning = word.usageWarning?.takeIf { it.isNotBlank() }
    val collocations = parseCollocations(word.collocations)
    if (!wordHasAnnotations(word)) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WordAnnotationBadges(word)
        nuance?.let {
            SectionLabel("语感")
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (collocations.isNotEmpty()) {
            SectionLabel("常见搭配")
            if (onTranslate != null && onSpeak != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    collocations.forEach { collocation ->
                        SpeakableEnglishText(
                            en = collocation,
                            pinnedZh = null,
                            ui = phraseStates[collocation],
                            onTranslate = onTranslate,
                            onSpeak = onSpeak,
                            speakingKey = speakingKey,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    collocations.forEach { collocation ->
                        AnnotationPill(
                            text = collocation,
                            bg = MaterialTheme.colorScheme.surfaceContainerHighest,
                            fg = MaterialTheme.colorScheme.onSurface,
                            // 搭配短语可能较长，允许折成两行而不是直接截断。
                            maxLines = 2,
                        )
                    }
                }
            }
        }
        warning?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        // 提醒语气只靠图标和容器色表达，读屏需要显式说明。
                        contentDescription = "使用提醒",
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
