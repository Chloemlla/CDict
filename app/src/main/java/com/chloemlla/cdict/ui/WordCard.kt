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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.WordEntity

/**
 * 共用英音/美音发音按钮；播放时显示停止状态，再次点击会停止播放。
 * 音频结束后播放状态由调用方自动清除。
 * 词典详情、背词学习卡与推荐卡共用。
 */
@Composable
fun PronunciationButtons(
    word: WordEntity,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    playingKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val ukPlaying = playingKey == "${word.id}:UK"
    val usPlaying = playingKey == "${word.id}:US"
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { onPlayPronunciation(word, Accent.UK) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = if (ukPlaying) "停止英式发音" else "播放英式发音"
                    stateDescription = if (ukPlaying) "正在播放，再次点击停止" else "未播放"
                },
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            if (ukPlaying) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("停止")
            } else {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("英音")
            }
        }
        FilledTonalButton(
            onClick = { onPlayPronunciation(word, Accent.US) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = if (usPlaying) "停止美式发音" else "播放美式发音"
                    stateDescription = if (usPlaying) "正在播放，再次点击停止" else "未播放"
                },
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            if (usPlaying) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("停止")
            } else {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("美音")
            }
        }
    }
}

/**
 * 共用词条内容：单词、词性标签、音标、翻译、释义和发音按钮。
 * 调用方可在卡片中追加词表标签、掌握状态、词频和语感标注等内容。
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
    playingKey: String? = null,
    speakingKey: String? = null,
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (pos != null) 10.dp else 6.dp),
            )
        }
        translation?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
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
                speakingKey = speakingKey,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        bottomContent?.invoke()
        PronunciationButtons(
            word = word,
            onPlayPronunciation = onPlayPronunciation,
            playingKey = playingKey,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** 词性缩写对应的中文名称。 */
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