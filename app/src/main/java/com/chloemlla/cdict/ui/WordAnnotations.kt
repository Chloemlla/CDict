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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.data.WordEntity

// Emotion-color palette. Kept theme-independent (like StudyScreen's green/red pairs) so the
// badges read identically in light and dark; register and collocation chips use theme tokens.
private val PositiveBg = Color(0xFFC8E6C9)
private val PositiveFg = Color(0xFF1B5E20)
private val NegativeBg = Color(0xFFFFCDD2)
private val NegativeFg = Color(0xFFB71C1C)
private val NeutralBg = Color(0xFFBBDEFB)
private val NeutralFg = Color(0xFF0D47A1)
private val ContextBg = Color(0xFFFFE0B2)
private val ContextFg = Color(0xFFE65100)

/** emotion_color enum value -> Chinese label; null when absent or unrecognized. */
fun emotionColorLabel(value: String?): String? = when (value?.trim()?.lowercase()) {
    "positive" -> "褒义"
    "negative" -> "贬义"
    "neutral" -> "中性"
    "context_dependent" -> "视语境"
    else -> null
}

/** register enum value -> Chinese label; falls back to the raw value when unrecognized. */
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

private fun emotionColors(value: String?): Pair<Color, Color>? = when (value?.trim()?.lowercase()) {
    "positive" -> PositiveBg to PositiveFg
    "negative" -> NegativeBg to NegativeFg
    "neutral" -> NeutralBg to NeutralFg
    "context_dependent" -> ContextBg to ContextFg
    else -> null
}

/** collocations column is a "；"-joined list (pipeline convention); comma forms tolerated too. */
fun parseCollocations(value: String?): List<String> =
    value?.split("；", ";", "、", ",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

/**
 * Parse the comma-separated aiSupplemented column into the set of fields that were
 * enriched from the distribution merge (phoneticUk / phoneticUs / mnemonic / derived /
 * sentences). Returns an empty set when the word carries no AI-supplemented content.
 */
fun supplementedFields(word: WordEntity): Set<String> =
    word.aiSupplemented?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

/** True when any of the word's displayed fields was added by the AI enrichment merge. */
fun wordHasAiSupplement(word: WordEntity): Boolean = supplementedFields(word).isNotEmpty()

/** True when the given merge field was supplemented by the AI enrichment for this word. */
fun fieldSupplemented(word: WordEntity, field: String): Boolean = field in supplementedFields(word)

/** True when any annotation field carries content worth rendering. */
fun wordHasAnnotations(word: WordEntity): Boolean =
    word.emotionColor?.isNotBlank() == true ||
        word.register?.isNotBlank() == true ||
        word.nuanceDescription?.isNotBlank() == true ||
        word.usageWarning?.isNotBlank() == true ||
        parseCollocations(word.collocations).isNotEmpty()

@Composable
private fun AnnotationPill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/**
 * Small "AI 补充" badge marking content that was enriched by the AI distribution merge
 * (phonetics, mnemonic, derived terms, sentences). Rendered next to the section that
 * carries supplemented content.
 */
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

/**
 * Places the "AI 补充" pill at the trailing edge of a section's content, i.e. right
 * after the distribution-supplemented data it marks. Renders nothing when [show]
 * is false.
 */
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

/** Emotion-color and register tags for a word; renders nothing when both are absent. */
@Composable
fun WordAnnotationBadges(word: WordEntity, modifier: Modifier = Modifier) {
    val emotion = emotionColors(word.emotionColor)
    val emotionLabel = emotionColorLabel(word.emotionColor)
    val register = registerLabel(word.register)
    if (emotion == null && register == null) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (emotion != null && emotionLabel != null) {
            AnnotationPill(text = emotionLabel, bg = emotion.first, fg = emotion.second)
        }
        register?.let {
            AnnotationPill(
                text = it,
                bg = MaterialTheme.colorScheme.secondaryContainer,
                fg = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * The full AI annotation block: badges, nuance description, collocations and the
 * highlighted usage-warning box. Shared by the word detail page and the study card.
 * When [onTranslate]/[onSpeak] are supplied, each collocation renders as a speakable,
 * auto-translated row; otherwise it renders as a static chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordAnnotationSection(
    word: WordEntity,
    modifier: Modifier = Modifier,
    phraseStates: Map<String, PhraseUiState> = emptyMap(),
    onTranslate: ((String) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
) {
    val nuance = word.nuanceDescription?.takeIf { it.isNotBlank() }
    val warning = word.usageWarning?.takeIf { it.isNotBlank() }
    val collocations = parseCollocations(word.collocations)
    if (!wordHasAnnotations(word)) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WordAnnotationBadges(word)
        nuance?.let {
            SectionLabel("语感")
            Text(it, style = MaterialTheme.typography.bodyMedium)
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    collocations.forEach { collocation ->
                        AnnotationPill(
                            text = collocation,
                            bg = MaterialTheme.colorScheme.surfaceContainerHighest,
                            fg = MaterialTheme.colorScheme.onSurface,
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
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(warning, style = MaterialTheme.typography.bodyMedium)
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
