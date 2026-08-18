package com.chloemlla.cdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.WordEntity

/**
 * Shared UK/US pronunciation buttons. Each button always shows the play icon;
 * clicking while the audio is already playing does nothing.
 * Used by the dictionary detail, study learn card, and recommendation card.
 */
@Composable
fun PronunciationButtons(
    word: WordEntity,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { onPlayPronunciation(word, Accent.UK) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("英音")
        }
        FilledTonalButton(
            onClick = { onPlayPronunciation(word, Accent.US) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("美音")
        }
    }
}

/**
 * Shared word card content: word, part-of-speech badge, phonetics, translation, definition,
 * and pronunciation buttons. Intended to be placed inside a Card with page-specific extras
 * (pool badge, mastered toggle, frequency info, annotations, etc.).
 */
@Composable
fun WordCardContent(
    word: WordEntity,
    phraseStates: Map<String, PhraseUiState>,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    onTranslate: (String) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
    showPartOfSpeech: Boolean = false,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val pos = if (showPartOfSpeech) {
        word.translation?.takeIf(String::isNotBlank)?.let { primaryPartOfSpeech(it) }
    } else null
    val phonetics = listOfNotNull(
        word.phoneticUk?.takeIf(String::isNotBlank)?.let { "英  $it" },
        word.phoneticUs?.takeIf(String::isNotBlank)?.let { "美  $it" },
    )
    val translation = word.translation?.takeIf(String::isNotBlank)
    val definition = word.definition?.takeIf(String::isNotBlank)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = word.word,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        pos?.takeIf { it.isNotBlank() }?.let {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    partOfSpeechLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (phonetics.isNotEmpty()) {
            Text(
                text = phonetics.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (pos != null) 10.dp else 6.dp),
            )
        }
        translation?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        definition?.let { def ->
            SpeakableEnglishText(
                en = def,
                pinnedZh = null,
                ui = phraseStates[def],
                onTranslate = onTranslate,
                onSpeak = onSpeak,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        bottomContent?.invoke()
        PronunciationButtons(
            word = word,
            onPlayPronunciation = onPlayPronunciation,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** Chinese label for a part-of-speech abbreviation. */
fun partOfSpeechLabel(pos: String): String = when (pos) {
    "n" -> "名词"
    "v" -> "动词"
    "adj" -> "形容词"
    "adv" -> "副词"
    "prep" -> "介词"
    "conj" -> "连词"
    "pron" -> "代词"
    "num" -> "数词"
    "art" -> "冠词"
    else -> pos
}